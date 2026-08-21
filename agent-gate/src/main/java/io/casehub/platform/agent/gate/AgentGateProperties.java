package io.casehub.platform.agent.gate;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.time.Duration;

@ConfigMapping(prefix = "casehub.platform.agent.gate")
public interface AgentGateProperties {

    @WithDefault("PT30S")
    Duration acquireTimeout();

    @WithDefault("PT5S")
    Duration queryAcquireTimeout();

    Concurrency concurrency();
    TokenBucketConfig tokenBucket();
    SlidingWindow slidingWindow();

    interface Concurrency {
        @WithDefault("0")
        int max();
    }

    interface TokenBucketConfig {
        @WithDefault("0.0")
        double permitsPerSecond();

        @WithDefault("0")
        int burstCapacity();
    }

    interface SlidingWindow {
        @WithDefault("0")
        int maxActions();

        @WithDefault("60")
        int windowSeconds();
    }

    Reaper reaper();

    interface Reaper {
        @WithDefault("60s")
        Duration scanInterval();

        @WithDefault("5m")
        Duration warnThreshold();

        @WithDefault("false")
        boolean forceCloseEnabled();

        @WithDefault("30m")
        Duration forceCloseThreshold();

        @WithDefault("24h")
        Duration maxRegistryAge();
    }
}
