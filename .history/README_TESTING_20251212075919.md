# Testing Guide

## Running Tests

### Unit Tests
```bash
mvn test
```

### Integration Tests
```bash
mvn verify
```

### Specific Test Class
```bash
mvn test -Dtest=LotLogicTest
```

## Test Coverage

### Unit Tests
- **LotLogicTest**: Tests FIFO/LIFO/HIFO tax lot allocation logic
- **TradeValidationServiceTest**: Tests validation gate rules
- **TradeClassifierTest**: Tests trade classification (CURRENT_DATED, FORWARD_DATED, BACKDATED)
- **HotpathPositionServiceTest**: Tests hotpath service logic
- **TradeProcessingServiceTest**: Tests main orchestration service

### Integration Tests
- **HotpathFlowIntegrationTest**: End-to-end hotpath flow test

## Test Scenarios Covered

### Tax Lot Logic
- ✅ Add new lot
- ✅ Reduce lots using FIFO
- ✅ Reduce lots using LIFO
- ✅ Reduce lots using HIFO
- ✅ Update lot prices (market data reset)
- ✅ Insufficient quantity error handling

### Validation
- ✅ Valid trade
- ✅ Missing required fields
- ✅ Negative quantity
- ✅ Invalid position key format
- ✅ Invalid trade type
- ✅ Future date validation
- ✅ Price bounds validation

### Trade Classification
- ✅ Current-dated trade (no snapshot)
- ✅ Forward-dated trade
- ✅ Backdated trade (with snapshot)
- ✅ Trade with snapshot same date

### Hotpath Processing
- ✅ New trade processing
- ✅ Increase trade processing
- ✅ Event versioning
- ✅ Snapshot updates
- ✅ Idempotency

### End-to-End Flow
- ✅ Complete hotpath flow: Validation -> Classification -> Processing
- ✅ Multiple trades (increase + decrease)
- ✅ FIFO lot allocation in complete flow

## Running Specific Test Scenarios

### Test FIFO Logic
```bash
mvn test -Dtest=LotLogicTest#testReduceLotsFIFO
```

### Test Validation
```bash
mvn test -Dtest=TradeValidationServiceTest
```

### Test Complete Flow
```bash
mvn test -Dtest=HotpathFlowIntegrationTest
```

## Test Data

Test data is generated in each test class. Key test scenarios:
- Trade ID: T12345, T-INTEGRATION-001, etc.
- Position Key: a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6 (valid hash format)
- Quantities: 100, 200, 500, 1000
- Prices: 50.00, 55.00, 60.00
- Dates: Current date, past dates, future dates

## Mocking Strategy

- **Repository Layer**: Mocked using Mockito
- **External Services**: Mocked (Contract Service, etc.)
- **Kafka**: Mocked in unit tests, can use Testcontainers for integration tests
- **Database**: H2 in-memory for integration tests

## Next Steps for Testing

1. Add Testcontainers for Kafka integration tests
2. Add Testcontainers for PostgreSQL integration tests
3. Add performance tests
4. Add chaos tests
5. Add contract testing (Pact)
