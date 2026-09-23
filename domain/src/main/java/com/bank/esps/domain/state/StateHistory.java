package com.bank.esps.domain.state;

import java.math.BigDecimal;
import java.time.LocalDate;

public record StateHistory(
        String tradeId,
        LocalDate effectiveDate,
        BigDecimal qtyBefore,
        BigDecimal qtyAfter,
        LifecycleStatus statusBefore,
        LifecycleStatus statusAfter,
        BigDecimal openPriceAfter) {
}
