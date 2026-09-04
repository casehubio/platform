package io.casehub.platform.api.capacity;

import java.util.List;
import java.util.Objects;

public record RedistributionContext(String actorId,
                                     CapacitySignal aggregatedSignal,
                                     List<CapacitySignal> sourceSignals) {

    public RedistributionContext {
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(aggregatedSignal);
        if (sourceSignals == null) { sourceSignals = List.of(); }
    }
}
