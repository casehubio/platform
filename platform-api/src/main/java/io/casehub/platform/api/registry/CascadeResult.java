package io.casehub.platform.api.registry;

import java.util.List;
import java.util.Objects;

public record CascadeResult(
        RegistryEntry deregistered,
        List<CascadeEffect> cascadeEffects
) {
    public CascadeResult {
        Objects.requireNonNull(cascadeEffects, "cascadeEffects");
        cascadeEffects = List.copyOf(cascadeEffects);
    }

    public static CascadeResult empty() {
        return new CascadeResult(null, List.of());
    }

    public static CascadeResult simple(RegistryEntry entry) {
        return new CascadeResult(entry, List.of());
    }

    public boolean hasFailures() {
        return cascadeEffects.stream().anyMatch(e -> e.failure() != null);
    }
}
