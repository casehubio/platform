package io.casehub.platform.api.label;

import io.casehub.platform.api.path.Path;

import java.util.Map;
import java.util.Objects;

public record LabelDescriptor(
        String tenancyId,
        Path path,
        String displayName,
        int sortOrder,
        Map<String, String> metadata
) {

    public LabelDescriptor {
        Objects.requireNonNull(tenancyId, "tenancyId must not be null");
        Objects.requireNonNull(path, "path must not be null");
        if (displayName == null || displayName.isBlank()) {
            var segments = path.segments();
            displayName = segments.isEmpty() ? path.value() : segments.get(segments.size() - 1);
        }
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public LabelDescriptor(String tenancyId, Path path, String displayName, int sortOrder) {
        this(tenancyId, path, displayName, sortOrder, Map.of());
    }
}
