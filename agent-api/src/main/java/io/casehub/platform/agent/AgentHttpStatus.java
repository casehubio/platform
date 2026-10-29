package io.casehub.platform.agent;

import io.casehub.platform.api.mcp.ComponentStatus;

import java.util.Map;
import java.util.Set;

public record AgentHttpStatus(
        Set<String> availableCapabilities,
        Map<String, Object> pools,
        int activeSessions,
        ComponentStatus overallStatus) {
}
