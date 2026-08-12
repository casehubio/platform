package io.casehub.platform.agent.openai;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.time.Duration;
import java.util.Optional;

@ConfigMapping(prefix = "casehub.platform.agent.openai")
public interface OpenAiAgentProperties {

    Optional<String> apiKey();

    @WithDefault("gpt-4.1")
    String defaultModel();

    @WithDefault("in_memory")
    String promptCacheRetention();

    @WithDefault("PT5M")
    Duration defaultTimeout();

    @WithDefault("4")
    int maxConcurrentSessions();
}
