package io.casehub.platform.agent.gate;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.time.Duration;

@ConfigMapping(prefix = "casehub.platform.agent.gate")
public interface AgentGateQuarkusProperties extends AgentGateProperties {

    @Override @WithDefault("PT30S")
    Duration acquireTimeout();

    @Override @WithDefault("PT5S")
    Duration queryAcquireTimeout();

    @Override
    QuarkusConcurrency concurrency();

    @Override
    QuarkusTokenBucketConfig tokenBucket();

    @Override
    QuarkusSlidingWindow slidingWindow();

    @Override
    QuarkusReaper reaper();

    interface QuarkusConcurrency extends Concurrency {
        @Override @WithDefault("0")
        int max();
    }

    interface QuarkusTokenBucketConfig extends TokenBucketConfig {
        @Override @WithDefault("0.0")
        double permitsPerSecond();

        @Override @WithDefault("0")
        int burstCapacity();
    }

    interface QuarkusSlidingWindow extends SlidingWindow {
        @Override @WithDefault("0")
        int maxActions();

        @Override @WithDefault("60")
        int windowSeconds();
    }

    interface QuarkusReaper extends Reaper {
        @Override @WithDefault("60s")
        Duration scanInterval();

        @Override @WithDefault("5m")
        Duration warnThreshold();

        @Override @WithDefault("false")
        boolean forceCloseEnabled();

        @Override @WithDefault("30m")
        Duration forceCloseThreshold();

        @Override @WithDefault("24h")
        Duration maxRegistryAge();
    }
}
