package io.casehub.platform.api.view;

import io.casehub.platform.api.path.Path;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record SubjectViewSpec(
    UUID id,
    String name,
    String tenancyId,
    String labelPattern,
    Path scope,
    String sortField,
    String sortDirection,
    Instant createdAt
) {
    public SubjectViewSpec {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(tenancyId, "tenancyId must not be null");
        Objects.requireNonNull(labelPattern, "labelPattern must not be null");
    }
}
