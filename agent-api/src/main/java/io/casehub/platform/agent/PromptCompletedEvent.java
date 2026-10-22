package io.casehub.platform.agent;

import java.time.Duration;
import java.time.Instant;

public record PromptCompletedEvent(String sessionId, String backendKey,
                                    Instant completedAt, Duration duration,
                                    boolean success, String failureReason) {}
