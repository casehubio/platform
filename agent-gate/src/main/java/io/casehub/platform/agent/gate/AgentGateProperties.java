package io.casehub.platform.agent.gate;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.time.Duration;

@ConfigMapping(prefix = "casehub.platform.agent.gate")
public interface AgentGateProperties {

    @WithDefault("0")
    int maxConcurrent();

    @WithDefault("0.0")
    double permitsPerSecond();

    @WithDefault("0")
    int burstCapacity();

    @WithDefault("PT30S")
    Duration acquireTimeout();

    @WithDefault("PT5S")
    Duration queryAcquireTimeout();
}
