package io.casehub.platform.agent.gate;

import java.time.Duration;

public interface AgentGateProperties {

    Duration acquireTimeout();
    Duration queryAcquireTimeout();
    Concurrency concurrency();
    TokenBucketConfig tokenBucket();
    SlidingWindow slidingWindow();
    Reaper reaper();

    interface Concurrency {
        int max();
    }

    interface TokenBucketConfig {
        double permitsPerSecond();
        int burstCapacity();
    }

    interface SlidingWindow {
        int maxActions();
        int windowSeconds();
    }

    interface Reaper {
        Duration scanInterval();
        Duration warnThreshold();
        boolean forceCloseEnabled();
        Duration forceCloseThreshold();
        Duration maxRegistryAge();
    }
}
