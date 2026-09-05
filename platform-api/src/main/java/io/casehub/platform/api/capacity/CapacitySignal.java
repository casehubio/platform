package io.casehub.platform.api.capacity;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record CapacitySignal(String actorId,
                              String signalType,
                              double pressure,
                              Instant observedAt,
                              Map<String, String> metadata) {

    public CapacitySignal {
        Objects.requireNonNull(actorId, "actorId is required");
        Objects.requireNonNull(signalType, "signalType is required");
        if (pressure < 0.0 || pressure > 1.0) {
            throw new IllegalArgumentException(
                    "Pressure must be between 0.0 and 1.0, got: " + pressure);
        }
        if (observedAt == null) { observedAt = Instant.now(); }
        if (metadata == null) { metadata = Map.of(); }
    }

    public CapacitySignal(String actorId, String signalType, double pressure, Instant observedAt) {
        this(actorId, signalType, pressure, observedAt, Map.of());
    }
}
