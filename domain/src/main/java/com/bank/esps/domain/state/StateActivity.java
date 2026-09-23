package com.bank.esps.domain.state;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * One execution or reset applied to a position. The position stores the result;
 * this object is the fact that caused it.
 */
public record StateActivity(
        String tradeId,
        ActivityType type,
        BigDecimal quantity,
        BigDecimal price,
        LocalDate effectiveDate,
        LocalDate settlementDate,
        String region,
        String account,
        String instrument,
        String currency) {

    public StateActivity {
        Objects.requireNonNull(tradeId, "tradeId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(effectiveDate, "effectiveDate");
        if (tradeId.isBlank()) {
            throw new IllegalArgumentException("tradeId is required");
        }
        switch (type) {
            case RESET, DIVIDEND -> requirePrice(price);
            case STOCK_SPLIT -> {
                Objects.requireNonNull(quantity, "ratio");
                if (quantity.signum() <= 0) {
                    throw new IllegalArgumentException("split ratio must be positive");
                }
            }
            default -> {
                requirePrice(price);
                Objects.requireNonNull(quantity, "quantity");
                if (quantity.signum() <= 0) {
                    throw new IllegalArgumentException("quantity must be positive");
                }
            }
        }
    }

    private static void requirePrice(BigDecimal price) {
        Objects.requireNonNull(price, "price");
        if (price.signum() < 0) {
            throw new IllegalArgumentException("price cannot be negative");
        }
    }
}
