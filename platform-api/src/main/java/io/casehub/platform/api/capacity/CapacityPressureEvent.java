package io.casehub.platform.api.capacity;

import java.util.Objects;

public record CapacityPressureEvent(String actorId,
                                     ActorCapacity capacity,
                                     double threshold,
                                     String triggerSignalType) {

    public CapacityPressureEvent {
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(capacity);
        Objects.requireNonNull(triggerSignalType);
    }
}
