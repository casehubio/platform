package io.casehub.platform.agent.geminicli;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.time.Duration;

@ConfigMapping(prefix = "casehub.platform.agent.gemini-cli")
public interface GeminiCliAgentProperties {

    @WithDefault("gemini")
    String binaryPath();

    @WithDefault("PT5M")
    Duration defaultTimeout();

    @WithDefault("4")
    int maxConcurrentSessions();
}
