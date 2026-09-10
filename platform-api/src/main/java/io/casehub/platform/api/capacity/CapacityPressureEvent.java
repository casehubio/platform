package io.casehub.platform.api.capacity;

import io.casehub.platform.api.subscription.SubscribableEvent;
import java.util.Objects;

public record CapacityPressureEvent(String actorId,
                                     ActorCapacity capacity,
                                     double threshold,
                                     String triggerSignalType,
                                     String tenancyId) implements SubscribableEvent {

    public static final String EVENT_TYPE = "capacity.pressure";

    public CapacityPressureEvent(String actorId, ActorCapacity capacity,
                                  double threshold, String triggerSignalType) {
        this(actorId, capacity, threshold, triggerSignalType, null);
    }

    public CapacityPressureEvent {
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(capacity);
        Objects.requireNonNull(triggerSignalType);
    }

    @Override
    public String type() {
        return EVENT_TYPE;
    }
}
