package com.bank.esps.domain.cdm.event;

import com.bank.esps.domain.cdm.base.Identifier;
import com.bank.esps.domain.cdm.position.CounterpartyPositionState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Lifecycle event of a position. Aligns with CDM
 * {@code CounterpartyPositionBusinessEvent}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CounterpartyPositionBusinessEvent {
    private PositionEventIntent intent;
    private String eventQualifier;
    private LocalDate eventDate;
    private LocalDate effectiveDate;
    private Identifier eventIdentifier;
    private Instruction instruction;
    @Builder.Default
    private List<CounterpartyPositionState> after = new ArrayList<>();

    public CounterpartyPositionState before() {
        return instruction != null ? instruction.getBefore() : null;
    }

    public CounterpartyPositionState primaryAfter() {
        if (after == null || after.isEmpty()) {
            return null;
        }
        return after.get(0);
    }

    public void validate() {
        if (intent == null) {
            throw new IllegalArgumentException("CounterpartyPositionBusinessEvent.intent is required");
        }
        if (eventDate == null) {
            throw new IllegalArgumentException("CounterpartyPositionBusinessEvent.eventDate is required");
        }
    }
}
