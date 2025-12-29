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
    private String positionKey; // Hash(Account+Instrument+Currency+Direction)
    
    // Base components for position key generation (required for sign change transitions)
    private String account;      // Account identifier
    private String instrument;   // Instrument identifier (e.g., security symbol)
    private String currency;     // Currency code (e.g., USD, EUR)
    
    private String tradeType; // NEW_TRADE, INCREASE, DECREASE
    private BigDecimal quantity;
    private BigDecimal price;
    private LocalDate effectiveDate; // Trade date (when trade was executed)
    private LocalDate settlementDate; // Settlement date (when interest accrual starts) - nullable, can be derived from PriceQuantitySchedule
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
