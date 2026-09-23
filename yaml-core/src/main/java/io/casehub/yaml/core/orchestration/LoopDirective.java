package io.casehub.yaml.core.orchestration;

import java.util.Map;

public sealed interface LoopDirective permits LoopDirective.Count, LoopDirective.CountUntil, LoopDirective.Until {

    record Count(int count) implements LoopDirective {
        public Count {
            if (count < 1) throw new IllegalArgumentException("Loop count must be >= 1, got " + count);
        }
    }

    record CountUntil(int count, String until) implements LoopDirective {
        public CountUntil {
            if (count < 1) throw new IllegalArgumentException("Loop count must be >= 1, got " + count);
            if (until == null || until.isBlank()) throw new IllegalArgumentException("Loop 'until' condition is required");
        }
    }

    record Until(String until) implements LoopDirective {
        public Until {
            if (until == null || until.isBlank()) throw new IllegalArgumentException("Loop 'until' condition is required");
        }
    }

    static LoopDirective parse(Object raw) {
        if (raw == null) throw new IllegalArgumentException("Loop directive value must not be null");
        if (raw instanceof LoopDirective d) return d;
        if (raw instanceof Number n) return new Count(n.intValue());
        if (raw instanceof Map<?, ?> m) {
            Object countObj = m.get("count");
            String until = (String) m.get("until");
            if (countObj != null && until != null) {
                return new CountUntil(((Number) countObj).intValue(), until);
            }
            if (countObj != null) {
                return new Count(((Number) countObj).intValue());
            }
            if (until != null) {
                return new Until(until);
            }
            throw new IllegalArgumentException("Loop map must contain 'count' and/or 'until'");
        }
        throw new IllegalArgumentException(
                "Invalid loop value: expected number, {count, until} map, or LoopDirective — got " + raw.getClass().getSimpleName());
    }
}
