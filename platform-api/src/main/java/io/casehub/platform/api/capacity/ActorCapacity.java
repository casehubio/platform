package io.casehub.platform.api.capacity;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record ActorCapacity(String actorId,
                             double aggregatePressure,
                             Map<String, Double> pressureBySignalType,
                             Instant observedAt) {

    public ActorCapacity {
        Objects.requireNonNull(actorId, "actorId is required");
        if (aggregatePressure < 0.0 || aggregatePressure > 1.0) {
            throw new IllegalArgumentException(
                    "aggregatePressure must be between 0.0 and 1.0, got: " + aggregatePressure);
        }
        if (pressureBySignalType == null) { pressureBySignalType = Map.of(); }
        if (observedAt == null) { observedAt = Instant.now(); }
    }
}
