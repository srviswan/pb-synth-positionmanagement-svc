package com.bank.esps.domain.cdm.base;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Generic identifier issued by a party. Aligns with CDM {@code Identifier}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Identifier {
    private String issuer;
    private IdentifierType identifierType;
    @Builder.Default
    private List<AssignedIdentifier> assignedIdentifier = new ArrayList<>();

    public static Identifier of(IdentifierType type, String value) {
        return Identifier.builder()
                .identifierType(type)
                .assignedIdentifier(List.of(AssignedIdentifier.builder().value(value).version(1).build()))
                .build();
    }

    public static Identifier of(IdentifierType type, String value, String issuer) {
        Identifier identifier = of(type, value);
        identifier.setIssuer(issuer);
        return identifier;
    }

    public String firstValue() {
        if (assignedIdentifier == null || assignedIdentifier.isEmpty()) {
            return null;
        }
        return assignedIdentifier.get(0).getValue();
    }
}
