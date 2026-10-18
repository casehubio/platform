package io.casehub.platform.agent.langchain4j;

import java.time.Duration;

/**
 * @deprecated This module is scheduled for removal. Use {@code AgentProvider} directly
 * with {@code AgentProvider.invokeBlocking()} and {@code ResponseFormat}
 * for structured output. See casehubio/platform#638.
 */
@Deprecated(forRemoval = true)
public interface AgentLangchain4jProperties {

    Duration closeTimeout();

    int sessionMemoryWindowSize();

    int maxConcurrentSessions();
}
