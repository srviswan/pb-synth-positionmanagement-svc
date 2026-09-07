package com.bank.esps.domain.cdm.product;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Contractual product with composable payouts and an underlier.
 * Aligns with CDM {@code NonTransferableProduct} + {@code EconomicTerms}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinancialProduct {
    private ProductType productType;
    private String productQualifier;
    private String currency;
    private Underlier underlier;
    @Builder.Default
    private List<PayoutLeg> payouts = new ArrayList<>();

    public static FinancialProduct equitySwap(String underlierId, String currency) {
        Underlier underlier = Underlier.equity(underlierId, currency);
        return FinancialProduct.builder()
                .productType(ProductType.SWAP)
                .productQualifier("EquitySwap")
                .currency(currency)
                .underlier(underlier)
                .payouts(new ArrayList<>(List.of(
                        PayoutLeg.performance(underlier, currency),
                        PayoutLeg.funding("SOFR", "1D", currency))))
                .build();
    }

    public static FinancialProduct equityCfd(String underlierId, String currency) {
        Underlier underlier = Underlier.equity(underlierId, currency);
        return FinancialProduct.builder()
                .productType(ProductType.CFD)
                .productQualifier("EquityCFD")
                .currency(currency)
                .underlier(underlier)
                .payouts(new ArrayList<>(List.of(PayoutLeg.performance(underlier, currency))))
                .build();
    }

    public static FinancialProduct indexSwap(String indexId, String currency) {
        Underlier underlier = Underlier.index(indexId, currency);
        return FinancialProduct.builder()
                .productType(ProductType.SWAP)
                .productQualifier("EquityIndexSwap")
                .currency(currency)
                .underlier(underlier)
                .payouts(new ArrayList<>(List.of(PayoutLeg.performance(underlier, currency))))
                .build();
    }

    public static FinancialProduct cibSwap(String identifier, String currency) {
        Underlier underlier = Underlier.cib(identifier, currency);
        return FinancialProduct.builder()
                .productType(ProductType.SWAP)
                .productQualifier("EquitySwap")
                .currency(currency)
                .underlier(underlier)
                .payouts(new ArrayList<>(List.of(
                        PayoutLeg.performance(underlier, currency),
                        PayoutLeg.funding("SOFR", "1D", currency))))
                .build();
    }

    public static FinancialProduct basketSwap(String basketId, String currency, List<BasketComponent> constituents) {
        Underlier underlier = Underlier.basket(basketId, currency, constituents);
        return FinancialProduct.builder()
                .productType(ProductType.SWAP)
                .productQualifier("EquityBasketSwap")
                .currency(currency)
                .underlier(underlier)
                .payouts(new ArrayList<>(List.of(PayoutLeg.performance(underlier, currency))))
                .build();
    }

    public String underlierId() {
        return underlier != null ? underlier.getIdentifier() : null;
    }

    public UnderlierType underlierType() {
        return underlier != null ? underlier.getType() : null;
    }
}
