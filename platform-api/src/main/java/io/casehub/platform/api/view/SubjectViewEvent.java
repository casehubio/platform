package io.casehub.platform.api.view;

import java.util.Objects;
import java.util.UUID;

public record SubjectViewEvent(
    UUID subjectId,
    UUID viewId,
    String viewName,
    ViewEventType type,
    String tenancyId
) {
    public SubjectViewEvent {
        Objects.requireNonNull(subjectId, "subjectId must not be null");
        Objects.requireNonNull(viewId, "viewId must not be null");
        Objects.requireNonNull(viewName, "viewName must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(tenancyId, "tenancyId must not be null");
    }
}
