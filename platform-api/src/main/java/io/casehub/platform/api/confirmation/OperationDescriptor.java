package io.casehub.platform.api.confirmation;

import java.util.Map;
import java.util.Objects;

public record OperationDescriptor(
    String summary,
    Map<String, String> metadata
) {
    public OperationDescriptor {
        Objects.requireNonNull(summary, "summary");
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    }
}
