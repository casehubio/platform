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
        Map<String, Object> extensions,
        int salience,
        Map<String, Object> clearing,
        @JsonProperty("max-prompt-tokens") int maxPromptTokens
) {
    public PoolDeclaration(String name, String agentId, String backend,
            int minActive, int maxActive, String workingDir,
            Map<String, Object> scaling, Map<String, Object> extensions) {
        this(name, agentId, backend, minActive, maxActive, workingDir,
                scaling, extensions, 0, null, 0);
    }

    public PoolDeclaration {
        Objects.requireNonNull(agentId, "agent-id is required");
        if (minActive < 0) throw new IllegalArgumentException("min-active must be >= 0");
        if (maxActive < 1) throw new IllegalArgumentException("max-active must be >= 1");
        if (maxActive < minActive) throw new IllegalArgumentException("max-active must be >= min-active");
        if (salience < 0) throw new IllegalArgumentException("salience must be >= 0");
        if (maxPromptTokens < 0) throw new IllegalArgumentException("maxPromptTokens must be >= 0");
        scaling = scaling != null ? Map.copyOf(scaling) : Map.of();
        extensions = extensions != null ? Map.copyOf(extensions) : Map.of();
        clearing = clearing != null ? Map.copyOf(clearing) : null;
    }
}
