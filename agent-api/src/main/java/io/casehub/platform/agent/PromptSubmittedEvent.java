package io.casehub.platform.agent;

import java.time.Instant;

public record PromptSubmittedEvent(String sessionId, String backendKey, Instant submittedAt) {}
