package io.casehub.platform.api.memory;

import java.time.Instant;
import java.util.Objects;

public record MemoryQuery(
    String entityId,
    MemoryDomain domain,
    String tenantId,
    String caseId,
    String question,
    int limit,
    Instant since
) {
    public MemoryQuery {
        Objects.requireNonNull(entityId, "entityId required");
        Objects.requireNonNull(domain,   "domain required");
        Objects.requireNonNull(tenantId, "tenantId required");
        if (limit < 1) throw new IllegalArgumentException("limit must be >= 1");
    }
}
