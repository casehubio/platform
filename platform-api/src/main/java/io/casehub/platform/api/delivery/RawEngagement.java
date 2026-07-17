package io.casehub.platform.api.delivery;

import java.util.Objects;

public record RawEngagement(
        String attemptId,
        EngagementType type,
        String metadata
) {
    public RawEngagement {
        Objects.requireNonNull(attemptId, "attemptId");
        Objects.requireNonNull(type, "type");
    }
}
