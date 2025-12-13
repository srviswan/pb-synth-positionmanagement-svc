package com.bank.esps.domain.event;

import com.bank.esps.domain.enums.TradeSequenceStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Trade event from upstream systems
 * CDM-inspired structure
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeEvent {
    private String tradeId;
    private String positionKey; // Hash(Account+Instrument+Currency)
    private String tradeType; // NEW_TRADE, INCREASE, DECREASE
    private BigDecimal quantity;
    private BigDecimal price;
    private LocalDate effectiveDate;
    private String contractId;
    private String correlationId;
    private String causationId;
    private String userId;
    
    // Classification (set by TradeClassifier)
    private TradeSequenceStatus sequenceStatus;
    
    /**
     * Check if trade increases position
     */
    public boolean isIncrease() {
        return "NEW_TRADE".equals(tradeType) || "INCREASE".equals(tradeType);
    }
    
    /**
     * Check if trade decreases position
     */
    public boolean isDecrease() {
        return "DECREASE".equals(tradeType);
    }
}
