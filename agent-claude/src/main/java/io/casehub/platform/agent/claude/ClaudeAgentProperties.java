package io.casehub.platform.agent.claude;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.time.Duration;
import java.util.Optional;

@ConfigMapping(prefix = "casehub.platform.agent.claude")
public interface ClaudeAgentProperties {

    /** Path to claude CLI binary. Resolved from PATH when absent. */
    Optional<String> binaryPath();

    /** Default wall-clock timeout when {@code AgentSessionConfig.timeout()} is null. */
    @WithDefault("PT5M")
    Duration defaultTimeout();

    /** Maximum concurrent agent sessions. Excess calls return a failure Multi. */
    @WithDefault("4")
    int maxConcurrentSessions();
}
