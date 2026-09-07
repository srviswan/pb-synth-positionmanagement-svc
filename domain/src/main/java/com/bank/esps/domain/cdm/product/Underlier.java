package com.bank.esps.domain.cdm.product;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Product underlier. Aligns with CDM {@code Observable} / {@code Underlier}.
 * Type-specific attributes are optional so new underlier families can be
 * added without a class explosion.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Underlier {
    private UnderlierType type;
    private String identifier;
    private String identifierScheme;
    private String description;
    private String currency;
    private String exchange;
    private String baseCurrency;
    private String quoteCurrency;
    private String referenceRate;
    private String tenor;
    private String creditName;
    private String instrumentClass;
    @Builder.Default
    private List<BasketComponent> constituents = new ArrayList<>();

    public static Underlier equity(String identifier, String currency) {
        return Underlier.builder()
                .type(UnderlierType.EQUITY)
                .identifier(identifier)
                .identifierScheme("TICKER")
                .currency(currency)
                .build();
    }

    public static Underlier index(String identifier, String currency) {
        return Underlier.builder()
                .type(UnderlierType.INDEX)
                .identifier(identifier)
                .identifierScheme("INDEX")
                .currency(currency)
                .build();
    }

    public static Underlier fx(String baseCurrency, String quoteCurrency) {
        return Underlier.builder()
                .type(UnderlierType.FX)
                .identifier(baseCurrency + quoteCurrency)
                .baseCurrency(baseCurrency)
                .quoteCurrency(quoteCurrency)
                .currency(quoteCurrency)
                .build();
    }

    public static Underlier interestRate(String referenceRate, String tenor, String currency) {
        return Underlier.builder()
                .type(UnderlierType.INTEREST_RATE)
                .identifier(referenceRate + "-" + tenor)
                .referenceRate(referenceRate)
                .tenor(tenor)
                .currency(currency)
                .build();
    }

    public static Underlier commodity(String identifier, String currency) {
        return Underlier.builder()
                .type(UnderlierType.COMMODITY)
                .identifier(identifier)
                .identifierScheme("COMMODITY")
                .currency(currency)
                .build();
    }

    public static Underlier credit(String creditName, String currency) {
        return Underlier.builder()
                .type(UnderlierType.CREDIT)
                .identifier(creditName)
                .creditName(creditName)
                .currency(currency)
                .build();
    }

    public static Underlier cib(String identifier, String currency) {
        return Underlier.builder()
                .type(UnderlierType.EQUITY)
                .identifier(identifier)
                .identifierScheme("CIB")
                .instrumentClass("CIB")
                .currency(currency)
                .build();
    }

    public static Underlier basket(String identifier, String currency, List<BasketComponent> constituents) {
        return Underlier.builder()
                .type(UnderlierType.BASKET)
                .identifier(identifier)
                .currency(currency)
                .constituents(constituents == null ? new ArrayList<>() : new ArrayList<>(constituents))
                .build();
    }
}
