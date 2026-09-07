package com.bank.esps.domain.cdm.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Corporate-action command that opens or closes dividend lots on a Position.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DividendInstruction {
    private String dividendId;
    private LocalDate exDate;
    private LocalDate payDate;
    private BigDecimal rate;
    private BigDecimal amount;
    private String currency;
    @Builder.Default
    private Action action = Action.OPEN;

    public enum Action {
        OPEN,
        CLOSE
    }
}
