package io.casehub.platform.agent.gemini;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.time.Duration;
import java.util.Optional;

@ConfigMapping(prefix = "casehub.platform.agent.gemini")
public interface GeminiAgentProperties {

    Optional<String> apiKey();

    @WithDefault("gemini-2.5-flash")
    String defaultModel();

    @WithDefault("PT1H")
    Duration cacheTtl();

    @WithDefault("PT5M")
    Duration defaultTimeout();

    @WithDefault("4")
    int maxConcurrentSessions();
}
