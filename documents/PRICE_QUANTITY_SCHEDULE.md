# PriceQuantity Schedule (CDM-Inspired)

## Overview
Added a `PriceQuantitySchedule` field to the position snapshot, inspired by CDM's (Common Domain Model) `PriceQuantity` data type. This tracks quantity and price pairs over time, similar to CDM's `MeasureSchedule`.

## CDM Reference

In CDM, `PriceQuantity` consists of:
- **Price**: `PriceSchedule` (extends `MeasureSchedule`) - price with dated values
- **Quantity**: `MeasureSchedule` - quantity with dated values (schedules of dated value pairs)

Our implementation follows a similar pattern, tracking both quantity and price together in a schedule.

## Implementation

### 1. Domain Model (PriceQuantitySchedule.java)

```java
public class PriceQuantitySchedule {
    private List<DatedPriceQuantity> schedule; // Dated quantity/price pairs
    private String unit;                       // "SHARES", "CONTRACTS", etc.
    private String currency;                   // "USD", "EUR", etc.
    
    public static class DatedPriceQuantity {
        private LocalDate effectiveDate;
        private BigDecimal quantity;
        private BigDecimal price;
        private BigDecimal getNotional(); // quantity × price
    }
}
```

### 2. Snapshot Entity (SnapshotEntity.java)

Added field:
```java
@JdbcTypeCode(SqlTypes.JSON)
@Column(name = "price_quantity_schedule", columnDefinition = "jsonb")
private String priceQuantitySchedule;
```

### 3. Database Migration (V2__add_price_quantity_schedule.sql)

```sql
ALTER TABLE snapshot_store 
ADD COLUMN price_quantity_schedule JSONB;
```

### 4. Service Logic (HotpathPositionService.java)

#### Initialization
- When creating a new snapshot, initializes schedule with first trade
- Sets unit ("SHARES") and currency ("USD")

#### Updates
- On each trade, adds/updates schedule entry for trade's effective date
- Calculates current quantity from position state
- Calculates weighted average price from open lots
- Maintains chronological order of schedule entries

## Schedule Structure

### JSON Format
```json
{
  "schedule": [
    {
      "effectiveDate": "2025-12-12",
      "quantity": 1000,
      "price": 50.00
    },
    {
      "effectiveDate": "2025-12-13",
      "quantity": 1500,
      "price": 52.50
    }
  ],
  "unit": "SHARES",
  "currency": "USD"
}
```

### Use Cases

1. **Historical Tracking**: See how quantity and price evolved over time
2. **Scheduled Changes**: Track forward-dated quantity/price changes
3. **Reporting**: Generate reports showing position evolution
4. **Reconciliation**: Compare scheduled vs. actual values
5. **Analytics**: Analyze price/quantity trends

## Methods

### PriceQuantitySchedule

- `addOrUpdate(date, quantity, price)`: Add or update entry for date
- `getForDate(date)`: Get quantity/price for specific date (most recent on or before)
- `getCurrent()`: Get most recent entry
- `getTotalNotional()`: Sum of all notionals in schedule
- `getRange(fromDate, toDate)`: Get entries within date range

### DatedPriceQuantity

- `getNotional()`: Calculate notional (quantity × price)

## Example Usage

### Scenario: Position Evolution

```
Day 1: NEW_TRADE
  - Schedule: [{date: "2025-12-12", qty: 1000, price: $50.00}]
  - Notional: $50,000

Day 2: INCREASE
  - Schedule: [
      {date: "2025-12-12", qty: 1000, price: $50.00},
      {date: "2025-12-13", qty: 1500, price: $52.50}  // Weighted avg
    ]
  - Notional: $78,750

Day 3: DECREASE
  - Schedule: [
      {date: "2025-12-12", qty: 1000, price: $50.00},
      {date: "2025-12-13", qty: 1500, price: $52.50},
      {date: "2025-12-14", qty: 1200, price: $53.00}  // Updated avg
    ]
  - Notional: $63,600
```

## Benefits

### ✅ CDM Compliance
- Follows CDM's PriceQuantity pattern
- Uses MeasureSchedule concept (dated values)
- Industry-standard structure

### ✅ Historical Tracking
- Complete history of quantity/price changes
- Chronological schedule of all updates
- Supports time-travel queries

### ✅ Reporting & Analytics
- Generate position evolution reports
- Analyze price/quantity trends
- Support regulatory reporting

### ✅ Reconciliation
- Compare scheduled vs. actual values
- Track forward-dated changes
- Validate position accuracy

## Integration Points

1. **Snapshot Creation**: Initializes schedule with first trade
2. **Snapshot Updates**: Updates schedule on each trade
3. **Price Calculation**: Uses weighted average from open lots
4. **Quantity Tracking**: Uses total quantity from position state

## Future Enhancements

1. **Forward-Dated Schedules**: Pre-populate schedule for known future changes
2. **Amortization**: Track scheduled amortization of quantities
3. **Price Resets**: Track scheduled price resets
4. **Query API**: Add endpoints to query schedule by date range

## Summary

- ✅ **CDM-Inspired**: Follows Common Domain Model's PriceQuantity pattern
- ✅ **Schedule Tracking**: Tracks quantity/price pairs over time
- ✅ **Database Field**: Added to snapshot_store table
- ✅ **Auto-Updated**: Maintained automatically on each trade
- ✅ **Historical View**: Complete history of position evolution

The PriceQuantity schedule provides a CDM-compliant way to track quantity and price evolution over time.
