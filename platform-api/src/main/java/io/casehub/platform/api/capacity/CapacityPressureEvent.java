package io.casehub.platform.api.capacity;

import java.time.Instant;
import java.util.Objects;

public record CapacityPressureEvent(String actorId,
                                     RedistributionDecision decision,
                                     CapacitySignal aggregatedSignal,
                                     Instant firedAt) {

    public CapacityPressureEvent {
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(decision);
        Objects.requireNonNull(aggregatedSignal);
        if (firedAt == null) { firedAt = Instant.now(); }
    }
}
