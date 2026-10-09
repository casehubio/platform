package io.casehub.yaml.core.orchestration;

import java.time.Duration;
import java.util.List;
import java.util.Map;

public sealed interface RetryDirective permits RetryDirective.Simple, RetryDirective.Full {

    record Simple(int max) implements RetryDirective {
        public Simple {
            if (max < 1) throw new IllegalArgumentException("Retry max must be >= 1, got " + max);
        }
    }

    record Full(int max, String backoff, Duration delay, List<String> on) implements RetryDirective {
        public Full(int max, String backoff, Duration delay) { this(max, backoff, delay, List.of()); }
        public Full {
            if (max < 1) throw new IllegalArgumentException("Retry max must be >= 1, got " + max);
            if (backoff == null || backoff.isBlank()) throw new IllegalArgumentException("Retry 'backoff' strategy is required");
            if (delay == null) throw new IllegalArgumentException("Retry 'delay' is required");
            if (on == null) on = List.of();
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
            @SuppressWarnings("unchecked")
            List<String> on = m.containsKey("on") ? (List<String>) m.get("on") : List.of();
            if (backoff != null && delayStr != null) {
                return new Full(max, backoff, DurationParser.parse(delayStr), on);
            }
            if (!on.isEmpty()) {
                return new Full(max, "fixed", Duration.ZERO, on);
            }
            return new Simple(max);
        }
        throw new IllegalArgumentException(
                "Invalid retry value: expected number, {max, backoff, delay} map, or RetryDirective — got " + raw.getClass().getSimpleName());
    }
}
