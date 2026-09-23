package io.casehub.yaml.core.orchestration;

import java.time.Duration;
import java.util.Map;

public sealed interface RetryDirective permits RetryDirective.Simple, RetryDirective.Full {

    record Simple(int max) implements RetryDirective {
        public Simple {
            if (max < 1) throw new IllegalArgumentException("Retry max must be >= 1, got " + max);
        }
    }

    record Full(int max, String backoff, Duration delay) implements RetryDirective {
        public Full {
            if (max < 1) throw new IllegalArgumentException("Retry max must be >= 1, got " + max);
            if (backoff == null || backoff.isBlank()) throw new IllegalArgumentException("Retry 'backoff' strategy is required");
            if (delay == null) throw new IllegalArgumentException("Retry 'delay' is required");
        }
    }

    static RetryDirective parse(Object raw) {
        if (raw == null) throw new IllegalArgumentException("Retry directive value must not be null");
        if (raw instanceof RetryDirective d) return d;
        if (raw instanceof Number n) return new Simple(n.intValue());
        if (raw instanceof Map<?, ?> m) {
            Object maxObj = m.get("max");
            if (maxObj == null) throw new IllegalArgumentException("Retry map must contain 'max'");
            int max = ((Number) maxObj).intValue();
            String backoff = (String) m.get("backoff");
            String delayStr = (String) m.get("delay");
            if (backoff != null && delayStr != null) {
                return new Full(max, backoff, DurationParser.parse(delayStr));
            }
            return new Simple(max);
        }
        throw new IllegalArgumentException(
                "Invalid retry value: expected number, {max, backoff, delay} map, or RetryDirective — got " + raw.getClass().getSimpleName());
    }
}
