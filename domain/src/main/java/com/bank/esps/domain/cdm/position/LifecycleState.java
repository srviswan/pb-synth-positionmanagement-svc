package com.bank.esps.domain.cdm.position;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * State of a position at a point in its lifecycle. Aligns with CDM {@code State}.
 *
 * <p>CDM condition {@code ClosedStateExists}: when {@code positionStatus} is
 * {@code CLOSED}, {@code closedState} must be present.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LifecycleState {
    private PositionStatus positionStatus;
    private ClosedState closedState;

    public static LifecycleState formed() {
        return LifecycleState.builder().positionStatus(PositionStatus.FORMED).build();
    }

    public static LifecycleState settled() {
        return LifecycleState.builder().positionStatus(PositionStatus.SETTLED).build();
    }

    public static LifecycleState closed(ClosedState closedState) {
        return LifecycleState.builder()
                .positionStatus(PositionStatus.CLOSED)
                .closedState(closedState)
                .build();
    }

    public boolean isClosed() {
        return positionStatus == PositionStatus.CLOSED;
    }

    public void validate() {
        if (positionStatus == PositionStatus.CLOSED && closedState == null) {
            throw new IllegalStateException("CDM ClosedStateExists: closedState is required when positionStatus is CLOSED");
        }
    }
}
