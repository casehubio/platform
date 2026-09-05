package io.casehub.platform.api.capacity;

import java.time.Duration;
import java.util.Objects;

public record RedistributionContext(String actorId,
                                     ActorCapacity capacity,
                                     String triggerSignalType,
                                     int openObligationCount,
                                     Duration timeSinceLastActivity) {

    public RedistributionContext {
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(capacity);
        if (triggerSignalType == null) { triggerSignalType = "unknown"; }
        if (timeSinceLastActivity == null) { timeSinceLastActivity = Duration.ZERO; }
    }
}
