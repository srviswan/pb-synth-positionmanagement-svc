# Lot P&L and Notional Tracking Implementation

## Overview
Enhanced the lot logic to track:
1. **Remaining Position** (quantity) - already existed
2. **Remaining Notional** (value = quantity × price) - newly added
3. **P&L Calculation** when closing lots - newly added

## Changes Made

### 1. TaxLot Model Enhancements (TaxLot.java)

#### Added Fields
- `originalQty`: Original quantity when lot was created (for P&L calculation)
- `originalPrice`: Original price (cost basis) when lot was created

#### Added Methods
- `getRemainingNotional()`: Calculates remaining notional = `remainingQty × currentRefPrice`
- `calculateRealizedPnL(closePrice, closedQty)`: Calculates realized P&L = `(closePrice - originalPrice) × closedQty`

### 2. LotAllocationResult Enhancements (LotAllocationResult.java)

#### Added Fields to LotAllocation
- `realizedPnL`: P&L when closing/reducing this lot

#### Added Methods
- `addReduction(lotId, qty, closePrice, realizedPnL)`: Records a reduction with P&L
- `getTotalRealizedPnL()`: Sums all realized P&L from allocations
- Updated `getAllocationsMap()`: Includes P&L in serialization

### 3. LotLogic Enhancements (LotLogic.java)

#### Updated `addLot()`
- Now tracks `originalQty` and `originalPrice` when creating lots
- Logs remaining notional

#### Updated `reduceLots()`
- Added `closePrice` parameter (price at which lots are being closed)
- Calculates P&L for each reduction using `calculateRealizedPnL()`
- Tracks remaining notional before and after reduction
- Logs P&L when lot is fully closed
- Uses `addReduction()` instead of `addAllocation()` to include P&L

### 4. Service Updates

#### HotpathPositionService
- Updated `reduceLots()` call to include `tradeEvent.getPrice()` as close price

#### RecalculationService
- Updated `reduceLots()` call to include `tradeEvent.getPrice()` as close price

## P&L Calculation Formula

### Realized P&L
```
P&L = (Close Price - Original Price) × Closed Quantity
```

### Example
```
Lot created:
  - Original Qty: 1000
  - Original Price: $50.00
  - Cost Basis: $50,000

Lot closed:
  - Closed Qty: 1000
  - Close Price: $60.00
  - Realized P&L = ($60.00 - $50.00) × 1000 = $10,000 (profit)
```

### Partial Close Example
```
Lot created:
  - Original Qty: 1000
  - Original Price: $50.00

Partial close:
  - Closed Qty: 500
  - Close Price: $55.00
  - Realized P&L = ($55.00 - $50.00) × 500 = $2,500 (profit)
  - Remaining Qty: 500
  - Remaining Notional: 500 × $55.00 = $27,500
```

## Remaining Notional Calculation

### Formula
```
Remaining Notional = Remaining Quantity × Current Reference Price
```

### Example
```
Lot:
  - Remaining Qty: 500
  - Current Ref Price: $55.00
  - Remaining Notional: 500 × $55.00 = $27,500
```

## Usage Examples

### Adding a Lot
```java
LotAllocationResult result = lotLogic.addLot(state, 
    new BigDecimal("1000"),  // qty
    new BigDecimal("50.00"), // price
    LocalDate.now());         // trade date

// Lot created with:
// - remainingQty: 1000
// - originalQty: 1000
// - originalPrice: $50.00
// - remainingNotional: $50,000
```

### Reducing/Closing Lots
```java
LotAllocationResult result = lotLogic.reduceLots(state,
    new BigDecimal("500"),   // qty to reduce
    contractRules,           // FIFO/LIFO/HIFO rules
    new BigDecimal("60.00")); // close price

// P&L calculated:
// - Closed Qty: 500
// - Original Price: $50.00
// - Close Price: $60.00
// - Realized P&L: ($60.00 - $50.00) × 500 = $5,000

// Result includes:
// - Total realized P&L: $5,000
// - Remaining notional: 500 × $60.00 = $30,000
```

## Benefits

### ✅ Complete Position Tracking
- **Remaining Position**: Quantity still open
- **Remaining Notional**: Value of remaining position
- **Cost Basis**: Original price tracked for P&L

### ✅ Accurate P&L Calculation
- Realized P&L calculated when closing lots
- Uses original cost basis vs. close price
- Supports partial closes

### ✅ Audit Trail
- P&L tracked in `LotAllocationResult`
- Can be serialized to JSON for reporting
- Total P&L available via `getTotalRealizedPnL()`

### ✅ Logging
- Logs remaining notional when adding lots
- Logs P&L when closing lots
- Logs notional changes when reducing lots

## Data Flow

```
1. Add Lot
   → Track originalQty, originalPrice
   → Calculate remainingNotional = remainingQty × currentRefPrice
   
2. Reduce Lot
   → Calculate P&L = (closePrice - originalPrice) × closedQty
   → Update remainingQty
   → Calculate new remainingNotional
   → If fully closed, log P&L
   
3. LotAllocationResult
   → Contains P&L for each reduction
   → getTotalRealizedPnL() sums all P&L
   → Serialized to JSON for reporting
```

## Summary

- ✅ **Remaining Position**: Tracked via `remainingQty` (already existed)
- ✅ **Remaining Notional**: Calculated via `getRemainingNotional()` (new)
- ✅ **P&L Calculation**: Calculated via `calculateRealizedPnL()` when closing lots (new)
- ✅ **Cost Basis Tracking**: `originalQty` and `originalPrice` tracked (new)
- ✅ **Audit Trail**: P&L included in `LotAllocationResult` (new)

The lot logic now provides complete position tracking with P&L calculation for closed lots.
