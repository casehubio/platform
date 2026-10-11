package io.casehub.platform.api.registry;

import java.time.Duration;
import java.util.Objects;

public record CascadeRule(
        String relationshipType,
        CascadeAction onSourceDeregister,
        CascadeAction onSourceExpired,
        Duration cascadeGracePeriod
) {
    public CascadeRule {
        Objects.requireNonNull(relationshipType, "relationshipType");
        Objects.requireNonNull(onSourceDeregister, "onSourceDeregister");
    }

    public CascadeRule(String relationshipType, CascadeAction onSourceDeregister) {
        this(relationshipType, onSourceDeregister, null, null);
    }

    public CascadeAction effectiveOnSourceExpired() {
        return onSourceExpired != null ? onSourceExpired : onSourceDeregister;
    }
}
