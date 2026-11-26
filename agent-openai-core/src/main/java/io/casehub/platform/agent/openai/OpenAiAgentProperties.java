package io.casehub.platform.agent.openai;

import java.time.Duration;
import java.util.Optional;

public interface OpenAiAgentProperties {

    Optional<String> apiKey();

    String defaultModel();

    default boolean store() {
        return false;
    }

    Duration defaultTimeout();

    int maxConcurrentSessions();
}
