package io.casehub.platform.agent.gate.spring;

import io.casehub.platform.agent.gate.AgentGateProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "casehub.platform.agent.gate")
public record AgentGateSpringProperties(
        @DefaultValue("PT30S") Duration acquireTimeout,
        @DefaultValue("PT5S") Duration queryAcquireTimeout,
        @DefaultValue ConcurrencyProperties concurrency,
        @DefaultValue TokenBucketProperties tokenBucket,
        @DefaultValue SlidingWindowProperties slidingWindow,
        @DefaultValue ReaperProperties reaper
) implements AgentGateProperties {

    public record ConcurrencyProperties(
            @DefaultValue("0") int max
    ) implements AgentGateProperties.Concurrency {}

    public record TokenBucketProperties(
            @DefaultValue("0.0") double permitsPerSecond,
            @DefaultValue("0") int burstCapacity
    ) implements AgentGateProperties.TokenBucketConfig {}

    public record SlidingWindowProperties(
            @DefaultValue("0") int maxActions,
            @DefaultValue("60") int windowSeconds
    ) implements AgentGateProperties.SlidingWindow {}

    public record ReaperProperties(
            @DefaultValue("60s") Duration scanInterval,
            @DefaultValue("5m") Duration warnThreshold,
            @DefaultValue("false") boolean forceCloseEnabled,
            @DefaultValue("30m") Duration forceCloseThreshold,
            @DefaultValue("24h") Duration maxRegistryAge
    ) implements AgentGateProperties.Reaper {}
}
