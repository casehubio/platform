package io.casehub.platform.api.governance;

import java.util.Map;
import java.util.Objects;

public record CoordinationExhaustedEvent(
    String operationId,
    String operationType,
    int triageAttempts,
    String abandonReason,
    Map<String, Object> lastFailureDetail
) {
    public CoordinationExhaustedEvent {
        Objects.requireNonNull(operationId, "operationId required");
        Objects.requireNonNull(operationType, "operationType required");
        Objects.requireNonNull(abandonReason, "abandonReason required");
        lastFailureDetail = lastFailureDetail != null ? Map.copyOf(lastFailureDetail) : Map.of();
    }
}
