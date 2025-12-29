# Settlement Date vs Trade Date: Quantity Tracking for Interest Accrual

## The Problem

For cashflow and interest calculations, we need to know:
1. **The exact settled quantity** (what quantity was actually settled)
2. **The settlement date** (when interest accrual starts)

However, `NonNegativeQuantitySchedule` only tracks:
- **Effective date** (trade date) - when quantity becomes effective
- **Quantity value** - the quantity amount

**The Gap**: 
- Trade Date: 2024-01-15 (quantity becomes effective)
- Settlement Date: 2024-01-17 (when interest accrual should start)
- **Problem**: `NonNegativeQuantitySchedule.datedValue.date = 2024-01-15` (trade date)
- **Need**: Settlement date (2024-01-17) to know when interest accrual starts

---

## CDM's Current Approach

### How CDM Handles This

CDM uses **calculation periods** for interest accrual, which are based on the **effective date** from the product definition:

```haskell
func CalculationPeriod:
    inputs:
        calculationPeriodDates CalculationPeriodDates (1..1)
        date date (1..1)
    output:
        result CalculationPeriodData (1..1)
    
    -- Uses effectiveDate from calculationPeriodDates
    adjustedStartDate = adjustDate(calculationPeriodDates.effectiveDate)
```

**CDM's Assumption**: For most products, **effective date ≈ settlement date** (or the difference is negligible).

**For Equity Swaps**: This assumption may not hold if there's a significant gap between trade date and settlement date (T+2, T+3).

---

## Solutions

### Solution 1: Store Settlement Date in TradeLot (Recommended)

**Approach**: Extend `TradeLot` to include settlement date information.

#### CDM-Aligned Extension

```java
// Extend TradeLot with settlement date
public class TradeLotWithSettlement {
    private TradeLot tradeLot;  // CDM TradeLot
    
    // Additional fields for settlement tracking
    private LocalDate settlementDate;  // When quantity was actually settled
    private LocalDate tradeDate;       // When trade was executed (from TradeLot)
    private BigDecimal settledQuantity; // Quantity that was settled
}
```

#### Storage in Position Service

```sql
CREATE TABLE trade_lots (
    lot_id VARCHAR(255) PRIMARY KEY,
    trade_id VARCHAR(255) NOT NULL,
    quantity DECIMAL(20, 8) NOT NULL,
    trade_date DATE NOT NULL,           -- Trade date (from CDM)
    settlement_date DATE NOT NULL,      -- Settlement date (when interest accrual starts)
    settled_quantity DECIMAL(20, 8) NOT NULL,  -- Quantity that was settled
    price_quantity JSONB NOT NULL,     -- Full CDM PriceQuantity
    created_at TIMESTAMP DEFAULT NOW()
);

-- Index for interest accrual queries
CREATE INDEX idx_settlement_date ON trade_lots (settlement_date);
```

#### Usage for Interest Calculation

```java
public BigDecimal calculateInterest(
        TradeLot lot,
        LocalDate calculationDate,
        BigDecimal interestRate) {
    
    // Use SETTLEMENT DATE for accrual start (not trade date)
    LocalDate accrualStartDate = lot.getSettlementDate();
    LocalDate accrualEndDate = calculationDate;
    
    // Calculate year fraction from settlement date
    BigDecimal yearFraction = calculateYearFraction(
        accrualStartDate,
        accrualEndDate,
        dayCountFraction
    );
    
    // Use settled quantity (not effective quantity)
    BigDecimal notional = lot.getSettledQuantity();
    
    // Calculate interest
    return notional.multiply(interestRate).multiply(yearFraction);
}
```

**Benefits**:
- ✅ Clear separation: trade date vs settlement date
- ✅ Accurate interest accrual (starts from settlement date)
- ✅ Tracks exact settled quantity
- ✅ CDM-compliant (extends CDM, doesn't modify it)

---

### Solution 2: Use PriceQuantity.effectiveDate for Settlement Date

**Approach**: Store settlement date in `PriceQuantity.effectiveDate` when quantity changes occur.

#### CDM Structure

```haskell
type PriceQuantity:
    quantity NonNegativeQuantitySchedule (0..*)
    effectiveDate AdjustableOrRelativeDate (0..1)  -- Use this for settlement date
```

**CDM Documentation**:
> "Specifies the date at which the price and quantity become effective. This day may be subject to adjustment in accordance with a business day convention, or could be specified as relative to a trade date, for instance. Optional cardinality, as the effective date is usually specified in the product definition, so it may only need to be specified as part of the PriceQuantity in an increase/decrease scenario for an existing trade."

**Interpretation**: For quantity changes (increases/decreases), `PriceQuantity.effectiveDate` can represent the **settlement date** of that quantity change.

#### Implementation

```java
// When creating a quantity change
PriceQuantity quantityChange = PriceQuantity.builder()
    .quantity(NonNegativeQuantitySchedule.builder()
        .value(Quantity.builder()
            .value(new BigDecimal("500000"))  // Additional quantity
            .unit(UnitType.builder()
                .financialUnit(FinancialUnitEnum.SHARE)
                .build())
            .build())
        .build())
    .effectiveDate(AdjustableOrRelativeDate.builder()
        .adjustableDate(AdjustableDate.builder()
            .unadjustedDate(LocalDate.of(2024, 1, 17))  // SETTLEMENT DATE
            .build())
        .build())
    .build();
```

**For Interest Calculation**:

```java
public BigDecimal getSettledQuantity(
        PriceQuantity priceQuantity,
        LocalDate calculationDate) {
    
    // Get effective date (settlement date) from PriceQuantity
    LocalDate settlementDate = resolveDate(
        priceQuantity.getEffectiveDate()
    );
    
    // Get quantity from schedule
    BigDecimal quantity = getQuantityScheduleStepValues(
        priceQuantity.getQuantity(),
        settlementDate  // Use settlement date, not calculation date
    );
    
    return quantity;
}
```

**Benefits**:
- ✅ Uses existing CDM structure
- ✅ No schema changes needed
- ✅ CDM-compliant

**Trade-offs**:
- ⚠️ `effectiveDate` is optional - may not always be present
- ⚠️ Requires discipline to always set `effectiveDate` = settlement date for quantity changes

---

### Solution 3: Store Settlement Date in Transfer (For Settled Quantities)

**Approach**: Link settled quantities to `Transfer.settlementDate` for historical tracking.

#### Structure

```java
// When a quantity is settled, create a Transfer record
Transfer settlementTransfer = Transfer.builder()
    .quantity(NonNegativeQuantity.builder()
        .value(cashAmount)  // Cash amount settled
        .unit(UnitType.builder().currency(CurrencyEnum.USD).build())
        .build())
    .settlementDate(AdjustableOrAdjustedOrRelativeDate.builder()
        .adjustedDate(LocalDate.of(2024, 1, 17))  // Settlement date
        .build())
    .settlementOrigin(payout)  // Links to which payout
    .build();

// Store link between quantity and settlement
QuantitySettlementLink link = QuantitySettlementLink.builder()
    .tradeLotId(lotId)
    .quantity(settledQuantity)  // Quantity that was settled
    .settlementDate(LocalDate.of(2024, 1, 17))
    .transferId(transferId)  // Links to Transfer
    .build();
```

#### Storage

```sql
CREATE TABLE quantity_settlement_links (
    link_id VARCHAR(255) PRIMARY KEY,
    trade_lot_id VARCHAR(255) NOT NULL,
    quantity DECIMAL(20, 8) NOT NULL,
    settlement_date DATE NOT NULL,
    transfer_id VARCHAR(255),  -- Links to Transfer
    created_at TIMESTAMP DEFAULT NOW(),
    
    FOREIGN KEY (trade_lot_id) REFERENCES trade_lots(lot_id),
    FOREIGN KEY (transfer_id) REFERENCES transfers(transfer_id)
);

-- Index for interest accrual queries
CREATE INDEX idx_lot_settlement_date 
ON quantity_settlement_links (trade_lot_id, settlement_date);
```

**Usage**:

```java
// Get settled quantity for a lot
public BigDecimal getSettledQuantity(
        String lotId,
        LocalDate calculationDate) {
    
    // Find most recent settlement before calculation date
    QuantitySettlementLink link = quantitySettlementRepository
        .findByLotIdAndSettlementDateBefore(lotId, calculationDate)
        .stream()
        .max(Comparator.comparing(QuantitySettlementLink::getSettlementDate))
        .orElse(null);
    
    if (link != null) {
        return link.getQuantity();
    }
    
    // Fallback: use quantity from schedule (trade date)
    return getQuantityFromSchedule(lotId, calculationDate);
}
```

**Benefits**:
- ✅ Historical tracking of settled quantities
- ✅ Links to Transfer for audit trail
- ✅ Supports partial settlements

**Trade-offs**:
- ⚠️ Additional table to maintain
- ⚠️ More complex queries

---

### Solution 4: Hybrid Approach (Recommended for Equity Swaps)

**Approach**: Combine Solutions 1 and 2 - store settlement date in TradeLot and use `PriceQuantity.effectiveDate` as fallback.

#### Implementation

```java
public class TradeLotSettlementTracker {
    
    /**
     * Get settled quantity for interest accrual.
     * Priority:
     * 1. Settlement date from TradeLot (if stored)
     * 2. effectiveDate from PriceQuantity (if present)
     * 3. Trade date (fallback)
     */
    public SettledQuantityInfo getSettledQuantity(
            TradeLot lot,
            LocalDate calculationDate) {
        
        // Try to get settlement date from TradeLot
        LocalDate settlementDate = lot.getSettlementDate();
        BigDecimal settledQuantity = lot.getSettledQuantity();
        
        // Fallback: use PriceQuantity.effectiveDate
        if (settlementDate == null) {
            PriceQuantity priceQuantity = lot.getPriceQuantity().get(0);
            if (priceQuantity.getEffectiveDate() != null) {
                settlementDate = resolveDate(
                    priceQuantity.getEffectiveDate()
                );
                settledQuantity = getQuantityFromSchedule(
                    priceQuantity.getQuantity(),
                    settlementDate
                );
            }
        }
        
        // Final fallback: use trade date
        if (settlementDate == null) {
            settlementDate = lot.getTradeDate();
            settledQuantity = getQuantityFromSchedule(
                lot.getPriceQuantity().get(0).getQuantity(),
                settlementDate
            );
        }
        
        return SettledQuantityInfo.builder()
            .quantity(settledQuantity)
            .settlementDate(settlementDate)
            .build();
    }
}
```

---

## Recommended Solution for Equity Swaps

### Implementation Strategy

**For New Trades (Execution)**:

```java
// When creating a new trade
TradeLot lot = TradeLot.builder()
    .priceQuantity(List.of(
        PriceQuantity.builder()
            .quantity(NonNegativeQuantitySchedule.builder()
                .value(Quantity.builder()
                    .value(new BigDecimal("1000000"))
                    .unit(UnitType.builder()
                        .financialUnit(FinancialUnitEnum.SHARE)
                        .build())
                    .build())
                .build())
            .effectiveDate(AdjustableOrRelativeDate.builder()
                .adjustableDate(AdjustableDate.builder()
                    .unadjustedDate(settlementDate)  // SETTLEMENT DATE
                    .build())
                .build())
            .build()
    ))
    .build();

// Store in Position Service with settlement date
positionService.createTradeLot(
    lot,
    tradeDate,        // 2024-01-15
    settlementDate,  // 2024-01-17
    settledQuantity  // 1,000,000 shares
);
```

**For Quantity Changes (Increase/Decrease)**:

```java
// When increasing quantity
PriceQuantity increase = PriceQuantity.builder()
    .quantity(NonNegativeQuantitySchedule.builder()
        .value(Quantity.builder()
            .value(new BigDecimal("500000"))  // Additional quantity
            .build())
        .build())
    .effectiveDate(AdjustableOrRelativeDate.builder()
        .adjustableDate(AdjustableDate.builder()
            .unadjustedDate(settlementDate)  // SETTLEMENT DATE of increase
            .build())
        .build())
    .build();

// Store with settlement date
positionService.updateTradeLot(
    lotId,
    increase,
    tradeDate,        // 2024-06-01
    settlementDate,  // 2024-06-03
    settledQuantity  // 500,000 additional shares
);
```

**For Interest Calculation**:

```java
public BigDecimal calculateInterest(
        String lotId,
        LocalDate calculationDate,
        BigDecimal interestRate,
        DayCountFraction dayCountFraction) {
    
    // Get settled quantity info
    SettledQuantityInfo settled = getSettledQuantity(lotId, calculationDate);
    
    // Calculate year fraction from SETTLEMENT DATE
    BigDecimal yearFraction = calculateYearFraction(
        settled.getSettlementDate(),  // Start from settlement date
        calculationDate,               // End at calculation date
        dayCountFraction
    );
    
    // Use settled quantity
    BigDecimal interest = settled.getQuantity()
        .multiply(interestRate)
        .multiply(yearFraction);
    
    return interest;
}
```

---

## Data Model Updates

### Position Service Schema

```sql
-- Enhanced trade_lots table
CREATE TABLE trade_lots (
    lot_id VARCHAR(255) PRIMARY KEY,
    trade_id VARCHAR(255) NOT NULL,
    partition_key VARCHAR(255) NOT NULL,
    
    -- Quantity information
    quantity DECIMAL(20, 8) NOT NULL,
    price_quantity JSONB NOT NULL,  -- Full CDM PriceQuantity
    
    -- Date information
    trade_date DATE NOT NULL,           -- Trade date (when executed)
    settlement_date DATE NOT NULL,      -- Settlement date (when interest accrual starts)
    
    -- Settled quantity (may differ from quantity if partial settlement)
    settled_quantity DECIMAL(20, 8) NOT NULL,
    
    -- Other fields
    direction VARCHAR(10) NOT NULL,
    uti VARCHAR(255),
    position_id VARCHAR(255),
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- Index for interest accrual queries
CREATE INDEX idx_settlement_date 
ON trade_lots (settlement_date, lot_id);

-- Index for quantity schedule queries
CREATE INDEX idx_trade_date 
ON trade_lots (trade_date, lot_id);
```

### Cashflow Service Schema

```sql
-- Track settled quantities for cashflow calculations
CREATE TABLE settled_quantity_history (
    history_id VARCHAR(255) PRIMARY KEY,
    trade_lot_id VARCHAR(255) NOT NULL,
    quantity DECIMAL(20, 8) NOT NULL,
    trade_date DATE NOT NULL,
    settlement_date DATE NOT NULL,
    calculation_period_start DATE NOT NULL,  -- When interest accrual starts
    calculation_period_end DATE,              -- When interest accrual ends (if settled)
    transfer_id VARCHAR(255),                -- Links to Transfer if settled
    created_at TIMESTAMP DEFAULT NOW(),
    
    FOREIGN KEY (trade_lot_id) REFERENCES trade_lots(lot_id)
);

-- Index for interest accrual queries
CREATE INDEX idx_lot_calc_period 
ON settled_quantity_history (trade_lot_id, calculation_period_start);
```

---

## Interest Calculation Logic

### Correct Approach

```java
public class InterestCalculationService {
    
    /**
     * Calculate interest for a period.
     * Interest accrues from SETTLEMENT DATE, not trade date.
     */
    public BigDecimal calculateInterest(
            String lotId,
            LocalDate periodStart,
            LocalDate periodEnd,
            BigDecimal interestRate,
            DayCountFraction dayCountFraction) {
        
        // Step 1: Get settled quantity info
        SettledQuantityInfo settled = getSettledQuantityInfo(lotId, periodStart);
        
        // Step 2: Determine accrual start date
        // Use MAX(settlement_date, period_start) to handle partial periods
        LocalDate accrualStart = settled.getSettlementDate()
            .isAfter(periodStart) ? 
            settled.getSettlementDate() : 
            periodStart;
        
        // Step 3: Calculate year fraction from accrual start
        BigDecimal yearFraction = calculateYearFraction(
            accrualStart,
            periodEnd,
            dayCountFraction
        );
        
        // Step 4: Use settled quantity (not effective quantity)
        BigDecimal notional = settled.getQuantity();
        
        // Step 5: Calculate interest
        return notional
            .multiply(interestRate)
            .multiply(yearFraction);
    }
    
    private SettledQuantityInfo getSettledQuantityInfo(
            String lotId,
            LocalDate asOfDate) {
        
        // Query trade lot with settlement date
        TradeLot lot = tradeLotRepository.findById(lotId);
        
        // Get quantity from schedule at settlement date
        BigDecimal quantity = getQuantityScheduleStepValues(
            lot.getPriceQuantity().get(0).getQuantity(),
            lot.getSettlementDate()  // Use settlement date, not asOfDate
        );
        
        return SettledQuantityInfo.builder()
            .quantity(quantity)
            .settlementDate(lot.getSettlementDate())
            .build();
    }
}
```

---

## Example: Trade with T+2 Settlement

### Scenario

- **Trade Date**: 2024-01-15
- **Settlement Date**: 2024-01-17 (T+2)
- **Quantity**: 1,000,000 shares
- **Interest Rate**: 2.5% per annum
- **Calculation Date**: 2024-02-15

### Storage

```json
{
  "tradeLot": {
    "lotId": "LOT-001",
    "priceQuantity": [{
      "quantity": [{
        "value": {
          "value": 1000000,
          "unit": {"financialUnit": "SHARE"}
        }
      }],
      "effectiveDate": {
        "adjustableDate": {
          "unadjustedDate": "2024-01-17"  // SETTLEMENT DATE
        }
      }
    }],
    "tradeDate": "2024-01-15",
    "settlementDate": "2024-01-17",
    "settledQuantity": 1000000
  }
}
```

### Interest Calculation

```
Accrual Period: 2024-01-17 to 2024-02-15
Days: 29 days
Year Fraction: 29 / 365 = 0.07945
Notional: 1,000,000 shares
Interest Rate: 2.5%
Interest = 1,000,000 × 0.025 × 0.07945 = $1,986.25
```

**Key Point**: Interest accrues from **2024-01-17** (settlement date), not **2024-01-15** (trade date).

---

## Summary

### The Problem

- `NonNegativeQuantitySchedule.datedValue.date` = **Trade Date** (when quantity becomes effective)
- Interest accrual should start from **Settlement Date** (when quantity is actually settled)
- **Gap**: No direct link between quantity schedule and settlement date

### The Solution

**Recommended Approach**: **Hybrid Solution (Solution 4)**

1. **Store settlement date in TradeLot** (Position Service)
2. **Use `PriceQuantity.effectiveDate`** as settlement date for quantity changes
3. **Use settlement date for interest accrual calculations** (not trade date)
4. **Track settled quantity separately** from effective quantity

### Implementation

- **Position Service**: Store `settlement_date` and `settled_quantity` in `trade_lots` table
- **Cashflow Service**: Use settlement date for interest accrual start
- **Lifecycle Engine**: Set `PriceQuantity.effectiveDate` = settlement date when creating quantity changes

### Key Principles

1. ✅ **Settlement date** determines when interest accrual starts
2. ✅ **Settled quantity** is what was actually settled (may differ from effective quantity)
3. ✅ **Trade date** is when trade was executed (for position tracking)
4. ✅ **Effective date** in quantity schedule can represent settlement date for quantity changes

This ensures accurate interest calculations while maintaining CDM compliance.
