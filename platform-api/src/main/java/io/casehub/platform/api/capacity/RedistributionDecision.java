package io.casehub.platform.api.capacity;

import java.time.Duration;
import java.util.Set;

public sealed interface RedistributionDecision {

    String reason();

    record Redistribute(String reason, Duration gracePeriod,
                        Set<String> excludeActors) implements RedistributionDecision {
        public Redistribute {
            if (gracePeriod == null) { gracePeriod = Duration.ZERO; }
            if (excludeActors == null) { excludeActors = Set.of(); }
        }
        public Redistribute(String reason) { this(reason, Duration.ZERO, Set.of()); }
    }

    record Compress(String reason) implements RedistributionDecision {}

    record Hold(String reason) implements RedistributionDecision {}

    record Escalate(String reason) implements RedistributionDecision {}

    static RedistributionDecision compress(String reason) { return new Compress(reason); }
    static RedistributionDecision redistribute(String reason) { return new Redistribute(reason); }
    static RedistributionDecision hold(String reason) { return new Hold(reason); }
    static RedistributionDecision escalate(String reason) { return new Escalate(reason); }
}
