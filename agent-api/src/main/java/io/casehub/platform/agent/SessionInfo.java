package io.casehub.platform.agent;

import java.time.Instant;

public record SessionInfo(String sessionId, String backendKey, Instant createdAt, int turnCount, Instant lastActivityAt) {
}
