package io.casehub.platform.agent;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

public record ResolvedAgent(
        String agentId,
        String systemPrompt,
        String model,
        Duration timeout,
        Map<String, String> metadata
) {
    public ResolvedAgent {
        Objects.requireNonNull(agentId, "agentId");
        Objects.requireNonNull(systemPrompt, "systemPrompt");
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    }
}
