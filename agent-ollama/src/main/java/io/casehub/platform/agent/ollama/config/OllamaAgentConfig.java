package io.casehub.platform.agent.ollama.config;

import io.casehub.platform.agent.ollama.OllamaAgentProperties;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.time.Duration;

@ConfigMapping(prefix = "casehub.platform.agent.ollama")
public interface OllamaAgentConfig extends OllamaAgentProperties {

    @Override
    @WithDefault("http://localhost:11434")
    String host();

    @Override
    @WithDefault("llama3")
    String defaultModel();

    @Override
    @WithDefault("PT120S")
    Duration defaultTimeout();

    @Override
    @WithDefault("4")
    int maxConcurrentSessions();
}
