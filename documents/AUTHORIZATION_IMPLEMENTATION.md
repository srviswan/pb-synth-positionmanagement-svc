# Authorization Implementation

## Overview

This document describes the authorization implementation for the Position Management Service, based on:
- `user_entitlements_architecture.md` - User data and entitlements architecture
- `messaging_entitlements_integration.md` - Messaging layer entitlements integration

## Implementation Summary

### 1. Domain Models

#### UserContext (`domain/src/main/java/com/bank/esps/domain/auth/UserContext.java`)
- Contains user authentication and authorization information
- Fields: userId, username, email, roles, permissions, accountIds, bookIds, sessionId, clientIp
- Helper methods: `hasRole()`, `hasPermission()`, `hasAccountAccess()`, `hasBookAccess()`

#### EntitlementMetadata (`domain/src/main/java/com/bank/esps/domain/auth/EntitlementMetadata.java`)
- Metadata attached to events and operations
- Fields: requiredFunction, requiredPermission, requiredRoles, authorizationStatus, authorizedAt, authorizedBy, signature

#### PositionFunction (`domain/src/main/java/com/bank/esps/domain/auth/PositionFunction.java`)
- Enum defining function-level entitlements for Position Management Service
- Functions: POSITION_VIEW, POSITION_UPDATE, TRADE_CREATE, TRADE_VIEW, DIAGNOSTICS_VIEW, DIAGNOSTICS_RECALCULATE, etc.

#### AuthorizationService (`domain/src/main/java/com/bank/esps/domain/auth/AuthorizationService.java`)
- Interface for authorization checks
- Methods: `hasEntitlement()`, `hasAccountAccess()`, `hasBookAccess()`, `getUserContext()`, etc.

### 2. Implementation Components

#### DefaultAuthorizationService (`application/src/main/java/com/bank/esps/application/service/authorization/DefaultAuthorizationService.java`)
- Implements `AuthorizationService` interface
- Integrates with IAM service via REST API
- Includes caching for performance (5-minute TTL by default)
- Falls back to allowing all requests if IAM service unavailable (development mode)
- **TODO**: In production, should fail closed (deny access if IAM service unavailable)

#### UserContextExtractor (`api/src/main/java/com/bank/esps/api/service/UserContextExtractor.java`)
- Extracts user context from HTTP requests
- Supports JWT tokens (Bearer token in Authorization header)
- Falls back to `X-User-Id` or `user-id` headers
- Creates anonymous context for development/testing

#### AuthorizationFilter (`api/src/main/java/com/bank/esps/api/filter/AuthorizationFilter.java`)
- Servlet filter that checks entitlements before processing requests
- Maps URL patterns to required functions
- Extracts user context and checks entitlements
- Returns 401 (Unauthorized) if no user context
- Returns 403 (Forbidden) if user lacks required permission
- Stores user context in request attribute for controllers

#### EventEnrichmentService (`application/src/main/java/com/bank/esps/application/service/authorization/EventEnrichmentService.java`)
- Enriches events with user context and entitlement metadata
- Determines required function based on event type

#### AuthorizedMessageProducer (`application/src/main/java/com/bank/esps/application/service/authorization/AuthorizedMessageProducer.java`)
- Wrapper around MessageProducer that adds authorization checks
- Checks entitlements before publishing messages

#### MessageConsumerAuthorizationInterceptor (`application/src/main/java/com/bank/esps/application/service/authorization/MessageConsumerAuthorizationInterceptor.java`)
- Interceptor for message consumers to check authorization
- Can filter messages based on user entitlements

### 3. API-Level Authorization

#### AuthorizationFilter
- Runs before all requests (Order 1)
- Checks entitlements based on URL pattern and HTTP method
- Maps:
  - `POST /api/trades` → `TRADE_CREATE`
  - `GET /api/positions` → `POSITION_VIEW`
  - `GET /api/diagnostics` → `DIAGNOSTICS_VIEW`
  - `POST /api/diagnostics/recalculate` → `DIAGNOSTICS_RECALCULATE`

#### Controller Updates
- **TradeController**: Checks `TRADE_CREATE` entitlement and book access
- **PositionController**: Checks `POSITION_VIEW` entitlement and book access
- **EventStoreController**: Checks `DIAGNOSTICS_RECALCULATE` entitlement and book access

### 4. Messaging-Level Authorization

#### Message Producers (Kafka & Solace)
- **KafkaMessageProducer**: Added `send()` overload that accepts `UserContext`
- Adds user context to message headers (`user-id`, `user-roles`, `user-accounts`)
- **SolaceMessageProducer**: Similar implementation with JMS message properties

#### Message Consumers
- **ColdpathRecalculationService**: Extracts user ID from Kafka message headers
- **SolaceBackdatedTradeConsumer**: Extracts user ID from JMS message properties
- Both log user context for audit purposes

### 5. Configuration

#### application.yml
```yaml
app:
  iam:
    service:
      url: ${IAM_SERVICE_URL:http://localhost:8081}
    cache:
      enabled: ${IAM_CACHE_ENABLED:true}
      ttl:
        minutes: ${IAM_CACHE_TTL_MINUTES:5}
  authorization:
    enabled: ${AUTHORIZATION_ENABLED:true}
    allow-anonymous: ${AUTHORIZATION_ALLOW_ANONYMOUS:false}
```

#### AuthorizationConfig (`api/src/main/java/com/bank/esps/api/config/AuthorizationConfig.java`)
- Configures `RestTemplate` for IAM service calls
- Connection timeout: 5 seconds
- Read timeout: 10 seconds

## Authorization Flow

### API Request Flow

```
1. Client Request (with JWT token or X-User-Id header)
   ↓
2. AuthorizationFilter
   - Extracts user context
   - Determines required function
   - Checks entitlement with IAM service
   - Returns 401/403 if unauthorized
   ↓
3. Controller
   - Gets user context from request attribute
   - Additional data access checks (account, book)
   - Processes request
   ↓
4. Service Layer
   - Uses user context for audit/logging
   - May pass user context to message producers
```

### Message Publishing Flow

```
1. Service calls messageProducer.send()
   ↓
2. MessageProducer (Kafka/Solace)
   - Adds user context to message headers/properties
   - Publishes to topic/queue
   ↓
3. Message Broker
   - Stores message with user context metadata
```

### Message Consumption Flow

```
1. Message Consumer receives message
   ↓
2. Extract user context from headers/properties
   ↓
3. Check authorization (optional, for filtering)
   ↓
4. Process message
```

## Function-Level Entitlements

### Position Management Functions

| Function | Description | Required Role |
|----------|-------------|--------------|
| `position:view` | View positions | POSITION_VIEWER, POSITION_MANAGER, POSITION_ADMIN |
| `position:update` | Update positions | POSITION_MANAGER, POSITION_ADMIN |
| `trade:create` | Create new trades | TRADE_CAPTURE_USER, TRADE_ADMIN |
| `trade:view` | View trades | TRADE_VIEWER, TRADE_CAPTURE_USER, TRADE_ADMIN |
| `diagnostics:view` | View diagnostics | SYSTEM_ADMIN, POSITION_ADMIN |
| `diagnostics:recalculate` | Trigger recalculation | SYSTEM_ADMIN, POSITION_ADMIN |

## Data Access Control

### Book-Level Access
- Users can only access positions/trades for books they have access to
- Checked via `authorizationService.hasBookAccess(userId, bookId)`
- Book information is stored in `TradeEvent.book` and `PositionState.book` fields

## Development Mode

### Current Behavior
- If IAM service is unavailable, authorization allows all requests (fail open)
- Anonymous users are allowed if `app.authorization.allow-anonymous=true`
- User context extraction falls back to headers or creates anonymous context

### Production Recommendations
- Set `app.authorization.allow-anonymous=false`
- Modify `DefaultAuthorizationService` to fail closed (return false if IAM service unavailable)
- Ensure all requests include valid JWT tokens
- Implement proper JWT validation in `UserContextExtractor`

## Integration with IAM Service

### Expected IAM Service API

The implementation expects an IAM service at `${app.iam.service.url}` with the following endpoints:

```
GET /api/v1/authorize?userId={userId}&function={functionName}
  → Returns: {"authorized": true/false}

GET /api/v1/authorize/account?userId={userId}&accountId={accountId}
  → Returns: {"authorized": true/false}

GET /api/v1/users/{userId}/permissions
  → Returns: ["permission1", "permission2", ...]

GET /api/v1/users/{userId}/roles
  → Returns: ["role1", "role2", ...]

GET /api/v1/users/{userId}/context
  → Returns: UserContext object
```

## Caching Strategy

- **Entitlement checks**: Cached for 5 minutes (configurable)
- **User permissions**: Cached for 5 minutes
- **User roles**: Cached for 5 minutes
- **User context**: Cached for 5 minutes
- **Cache invalidation**: Manual (via `invalidateUserCache()` method)

## Security Considerations

### Current Implementation
- ✅ Function-level entitlements checked
- ✅ Book-level access control (primary data access control)
- ✅ User context extraction from JWT/headers
- ✅ Authorization filter for all API requests
- ✅ User context in message headers
- ⚠️ Development mode allows anonymous access (configurable)
- ⚠️ Fail open if IAM service unavailable (should be fail closed in production)

### Production Recommendations
1. **JWT Validation**: Implement proper JWT token validation and signature verification
2. **Fail Closed**: Modify to deny access if IAM service unavailable
3. **Rate Limiting**: Add rate limiting for authorization checks
4. **Audit Logging**: Enhanced audit logging for authorization decisions
5. **Token Refresh**: Implement token refresh mechanism
6. **MFA**: Support multi-factor authentication for sensitive operations

## Testing

### Manual Testing
1. Test with valid JWT token
2. Test with X-User-Id header
3. Test with missing authorization (should return 401)
4. Test with insufficient permissions (should return 403)
5. Test book access restrictions

### Configuration for Testing
```bash
# Enable authorization
export AUTHORIZATION_ENABLED=true

# Allow anonymous for development
export AUTHORIZATION_ALLOW_ANONYMOUS=true

# IAM service URL
export IAM_SERVICE_URL=http://localhost:8081
```

## Next Steps

1. **JWT Implementation**: Complete JWT token decoding and validation
2. **IAM Service Integration**: Connect to actual IAM service or create mock
3. **Production Hardening**: Change fail-open to fail-closed behavior
4. **Enhanced Filtering**: Add message filtering in consumers based on entitlements
5. **Audit Logging**: Add comprehensive audit logging for authorization decisions
6. **Performance Testing**: Test authorization overhead and caching effectiveness

## Files Created/Modified

### New Files
- `domain/src/main/java/com/bank/esps/domain/auth/UserContext.java`
- `domain/src/main/java/com/bank/esps/domain/auth/EntitlementMetadata.java`
- `domain/src/main/java/com/bank/esps/domain/auth/PositionFunction.java`
- `domain/src/main/java/com/bank/esps/domain/auth/AuthorizationService.java`
- `application/src/main/java/com/bank/esps/application/service/authorization/DefaultAuthorizationService.java`
- `application/src/main/java/com/bank/esps/application/service/authorization/EventEnrichmentService.java`
- `application/src/main/java/com/bank/esps/application/service/authorization/AuthorizedMessageProducer.java`
- `application/src/main/java/com/bank/esps/application/service/authorization/MessageConsumerAuthorizationInterceptor.java`
- `api/src/main/java/com/bank/esps/api/service/UserContextExtractor.java`
- `api/src/main/java/com/bank/esps/api/filter/AuthorizationFilter.java`
- `api/src/main/java/com/bank/esps/api/config/AuthorizationConfig.java`

### Modified Files
- `api/src/main/java/com/bank/esps/api/controller/TradeController.java` - Added authorization checks
- `api/src/main/java/com/bank/esps/api/controller/PositionController.java` - Added authorization checks
- `api/src/main/java/com/bank/esps/api/controller/EventStoreController.java` - Added authorization checks
- `api/src/main/java/com/bank/esps/api/filter/CorrelationIdFilter.java` - Updated order
- `infrastructure/src/main/java/com/bank/esps/infrastructure/messaging/kafka/KafkaMessageProducer.java` - Added user context support
- `infrastructure/src/main/java/com/bank/esps/infrastructure/messaging/solace/SolaceMessageProducer.java` - Added user context support
- `domain/src/main/java/com/bank/esps/domain/messaging/MessageProducer.java` - Added user context overload
- `application/src/main/java/com/bank/esps/application/service/ColdpathRecalculationService.java` - Extract user ID from headers
- `application/src/main/java/com/bank/esps/application/service/SolaceBackdatedTradeConsumer.java` - Extract user ID from JMS properties
- `application/pom.xml` - Added spring-web dependency
- `api/src/main/resources/application.yml` - Added IAM and authorization configuration
