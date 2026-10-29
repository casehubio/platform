package io.casehub.platform.agent;

import java.time.Instant;

public record SessionHandle(String sessionId, String backendKey, Instant createdAt, int timeoutSeconds) {
}
