package io.casehub.platform.agent;

import java.util.Map;
import java.util.Objects;

public record AgentDescriptor(
        String agentId,
        String displayName,
        String description,
        Map<String, String> metadata
) {
    public AgentDescriptor {
        Objects.requireNonNull(agentId, "agentId");
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    }
}
