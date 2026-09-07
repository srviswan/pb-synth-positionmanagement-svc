package com.bank.esps.domain.cdm.product;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One economic leg of a contractual product. Aligns with CDM {@code Payout}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayoutLeg {
    private PayoutType payoutType;
    private String payer;
    private String receiver;
    private Underlier underlier;
    private String currency;
    private String dayCount;
    private String returnType;

    public static PayoutLeg performance(Underlier underlier, String currency) {
        return PayoutLeg.builder()
                .payoutType(PayoutType.PERFORMANCE)
                .underlier(underlier)
                .currency(currency)
                .returnType("TOTAL_RETURN")
                .build();
    }

    public static PayoutLeg funding(String referenceRate, String tenor, String currency) {
        return PayoutLeg.builder()
                .payoutType(PayoutType.INTEREST_RATE)
                .underlier(Underlier.interestRate(referenceRate, tenor, currency))
                .currency(currency)
                .build();
    }
}
