# Entitlements Configuration

## Overview

Entitlements are now loaded from a YAML configuration file instead of being hardcoded in the enum. This allows for dynamic configuration of function-level entitlements and URL mappings without code changes.

## Configuration File

The entitlements configuration file is located at:
- Default: `api/src/main/resources/entitlements.yml`
- Configurable via: `app.authorization.entitlements.file` property

## File Format

The `entitlements.yml` file contains two main sections:

### 1. Entitlements

Defines all available function-level entitlements:

```yaml
entitlements:
  position:view: "View positions"
  position:search: "Search positions"
  position:export: "Export position data"
  position:update: "Update positions"
  position:adjust: "Adjust positions"
  trade:create: "Create new trades"
  trade:view: "View trades"
  trade:increase: "Increase trade quantity"
  trade:decrease: "Decrease trade quantity"
  trade:terminate: "Terminate trades"
  diagnostics:view: "View diagnostics"
  diagnostics:recalculate: "Trigger recalculation"
  diagnostics:admin: "Diagnostics administration"
  position:admin: "Position administration"
```

### 2. URL Mappings

Maps HTTP methods and URL patterns to required functions:

```yaml
urlMappings:
  - method: "POST"
    pattern: "/api/trades"
    function: "trade:create"
  
  - method: "GET"
    pattern: "/api/trades/.*"
    function: "trade:view"
  
  - method: "GET"
    pattern: "/api/positions"
    function: "position:view"
  
  - method: "GET"
    pattern: "/api/positions/.*"
    function: "position:view"
  
  - method: "PUT"
    pattern: "/api/positions/.*"
    function: "position:update"
  
  - method: "GET"
    pattern: "/api/diagnostics"
    function: "diagnostics:view"
  
  - method: "POST"
    pattern: "/api/diagnostics/recalculate"
    function: "diagnostics:recalculate"
  
  - method: "POST"
    pattern: "/api/diagnostics/recalculate/async"
    function: "diagnostics:recalculate"
```

## Pattern Matching

URL patterns use Java regular expressions:
- `/api/trades` - Exact match
- `/api/trades/.*` - Matches `/api/trades/` followed by any characters
- `/api/positions/.*` - Matches `/api/positions/` followed by any characters

## Adding New Entitlements

1. Add the entitlement to the `entitlements` section:
   ```yaml
   entitlements:
     new:function: "Description of new function"
   ```

2. Add URL mapping if needed:
   ```yaml
   urlMappings:
     - method: "POST"
       pattern: "/api/new-endpoint"
       function: "new:function"
   ```

3. Restart the application (or use Spring Boot DevTools for hot reload)

## Fallback Behavior

If the entitlements file cannot be loaded:
- The system falls back to default entitlements (hardcoded)
- A warning is logged
- The application continues to function

## Configuration Properties

```yaml
app:
  authorization:
    enabled: true
    allow-anonymous: false
    entitlements:
      file: entitlements.yml  # Path relative to classpath
```

## Environment Variables

```bash
# Enable/disable authorization
export AUTHORIZATION_ENABLED=true

# Allow anonymous access (development)
export AUTHORIZATION_ALLOW_ANONYMOUS=false

# Custom entitlements file path
export ENTITLEMENTS_FILE=entitlements.yml
```

## Service Implementation

The `EntitlementConfigurationService` loads entitlements at startup:
- Loads from classpath resource
- Parses YAML using SnakeYAML
- Caches entitlements and URL mappings in memory
- Provides methods to query entitlements and find required functions

## Benefits

1. **No Code Changes**: Add/modify entitlements without recompiling
2. **Flexibility**: Different environments can have different entitlements
3. **Maintainability**: Centralized configuration
4. **Version Control**: Entitlements tracked in Git
5. **Hot Reload**: Can be reloaded with Spring Boot DevTools

## Example: Adding a New Endpoint

1. Create new endpoint in controller
2. Add entitlement:
   ```yaml
   entitlements:
     reports:generate: "Generate reports"
   ```
3. Add URL mapping:
   ```yaml
   urlMappings:
     - method: "GET"
       pattern: "/api/reports/generate"
       function: "reports:generate"
   ```
4. Restart application

The new endpoint will automatically be protected with the new entitlement.
