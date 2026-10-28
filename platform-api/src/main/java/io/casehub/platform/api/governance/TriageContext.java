package io.casehub.platform.api.governance;

import java.util.Map;
import java.util.Objects;

public record TriageContext(
    String operationId,
    String operationType,
    int attemptNumber,
    int maxAttempts,
    String failureSummary,
    Map<String, Object> failureDetail,
    Map<String, Object> executionContext
) {
    public TriageContext {
        Objects.requireNonNull(operationId, "operationId required");
        Objects.requireNonNull(operationType, "operationType required");
        Objects.requireNonNull(failureSummary, "failureSummary required");
        failureDetail = failureDetail != null ? Map.copyOf(failureDetail) : Map.of();
        executionContext = executionContext != null ? Map.copyOf(executionContext) : Map.of();
        if (attemptNumber < 1) throw new IllegalArgumentException("attemptNumber must be >= 1");
        if (maxAttempts < 1) throw new IllegalArgumentException("maxAttempts must be >= 1");
    }
}
