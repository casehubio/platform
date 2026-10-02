package io.casehub.platform.agent.ollama;

import java.time.Duration;

public interface OllamaAgentProperties {

    String host();

    String defaultModel();

    Duration defaultTimeout();

    int maxConcurrentSessions();
}
