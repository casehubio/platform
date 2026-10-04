package io.casehub.platform.agent.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import java.util.Objects;

public record PoolDeclaration(
        String name,
        @JsonProperty("agent-id") String agentId,
        String backend,
        @JsonProperty("min-active") int minActive,
        @JsonProperty("max-active") int maxActive,
        @JsonProperty("working-dir") String workingDir,
        Map<String, Object> scaling,
        Map<String, Object> extensions
) {
    public PoolDeclaration {
        Objects.requireNonNull(agentId, "agent-id is required");
        if (minActive < 0) throw new IllegalArgumentException("min-active must be >= 0");
        if (maxActive < 1) throw new IllegalArgumentException("max-active must be >= 1");
        if (maxActive < minActive) throw new IllegalArgumentException("max-active must be >= min-active");
        scaling = scaling != null ? Map.copyOf(scaling) : Map.of();
        extensions = extensions != null ? Map.copyOf(extensions) : Map.of();
    }
}
