package io.casehub.platform.api.delivery;

import java.util.Objects;

public record EngagementRecorded(EngagementEvent event) {
    public EngagementRecorded {
        Objects.requireNonNull(event, "event");
    }
}
