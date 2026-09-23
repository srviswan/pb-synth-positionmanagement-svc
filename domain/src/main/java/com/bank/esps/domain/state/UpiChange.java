package com.bank.esps.domain.state;

import java.time.LocalDate;

public record UpiChange(
        String upi,
        String previousUpi,
        UpiChangeType changeType,
        String tradeId,
        LocalDate effectiveDate) {
}
