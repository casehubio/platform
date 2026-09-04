package io.casehub.platform.api.capacity;

import java.time.Instant;
import java.util.Objects;

public record CapacitySignal(String actorId,
                              String source,
                              double pressure,
                              Instant timestamp) {

    public CapacitySignal {
        Objects.requireNonNull(actorId, "actorId is required");
        Objects.requireNonNull(source, "source is required");
        if (pressure < 0.0 || pressure > 1.0) {
            throw new IllegalArgumentException(
                    "Pressure must be between 0.0 and 1.0, got: " + pressure);
        }
        if (timestamp == null) { timestamp = Instant.now(); }
    }
}
