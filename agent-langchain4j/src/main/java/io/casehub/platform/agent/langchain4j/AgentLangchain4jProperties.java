package io.casehub.platform.agent.langchain4j;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.time.Duration;

@ConfigMapping(prefix = "casehub.platform.agent.langchain4j")
public interface AgentLangchain4jProperties {

    @WithDefault("PT30S")
    Duration closeTimeout();

    @WithDefault("20")
    int sessionMemoryWindowSize();

    @WithDefault("10")
    int maxConcurrentSessions();
}
