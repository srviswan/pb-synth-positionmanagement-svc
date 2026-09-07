package com.bank.esps.domain.cdm.position;

import com.bank.esps.domain.cdm.base.AssignedIdentifier;
import com.bank.esps.domain.cdm.base.IdentifierType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Position identifier, including UPI. Aligns with CDM {@code PositionIdentifier}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PositionIdentifier {
    private String issuer;
    private IdentifierType identifierType;
    @Builder.Default
    private List<AssignedIdentifier> assignedIdentifier = new ArrayList<>();

    public static PositionIdentifier upi(String value) {
        return PositionIdentifier.builder()
                .identifierType(IdentifierType.UTI)
                .assignedIdentifier(List.of(AssignedIdentifier.builder().value(value).version(1).build()))
                .build();
    }

    public static PositionIdentifier positionKey(String value) {
        return PositionIdentifier.builder()
                .identifierType(IdentifierType.POSITION_KEY)
                .assignedIdentifier(List.of(AssignedIdentifier.builder().value(value).version(1).build()))
                .build();
    }

    public String firstValue() {
        if (assignedIdentifier == null || assignedIdentifier.isEmpty()) {
            return null;
        }
        return assignedIdentifier.get(0).getValue();
    }
}
