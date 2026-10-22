package io.casehub.platform.compiler;

import io.casehub.yaml.core.orchestration.DurationParser;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

record DecoratorStack(String when, Duration timeout, RetrySpec retry, Integer loop, String mutex) {

    static final DecoratorStack EMPTY = new DecoratorStack(null, null, null, null, null);

    private static final Set<String> DECORATOR_KEYS = Set.of("when", "timeout", "retry", "loop", "mutex", "step", "meta");

    static DecoratorStack extract(Map<String, Object> step) {
        String when = step.containsKey("when") ? String.valueOf(step.get("when")) : null;
        Duration timeout = step.containsKey("timeout")
                ? DurationParser.parse(String.valueOf(step.get("timeout"))) : null;
        RetrySpec retry = step.containsKey("retry") ? RetrySpec.from(step.get("retry")) : null;
        Integer loop = step.containsKey("loop") ? ((Number) step.get("loop")).intValue() : null;
        String mutex = step.containsKey("mutex") ? String.valueOf(step.get("mutex")) : null;
        return new DecoratorStack(when, timeout, retry, loop, mutex);
    }

    static Map<String, Object> strip(Map<String, Object> step) {
        var result = new LinkedHashMap<String, Object>();
        for (var entry : step.entrySet()) {
            if (!DECORATOR_KEYS.contains(entry.getKey())) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    boolean isEmpty() {
        return when == null && timeout == null && retry == null && loop == null && mutex == null;
    }

    DecoratorStack merge(DecoratorStack other) {
        if (other == null) return this;
        return new DecoratorStack(
                when != null ? when : other.when,
                timeout != null ? timeout : other.timeout,
                retry != null ? retry : other.retry,
                loop != null ? loop : other.loop,
                mutex != null ? mutex : other.mutex);
    }

    record RetrySpec(int max) {
        static RetrySpec from(Object value) {
            if (value instanceof Number n) return new RetrySpec(n.intValue());
            if (value instanceof Map<?, ?> m) {
                return new RetrySpec(m.containsKey("max") ? ((Number) m.get("max")).intValue() : 1);
            }
            return new RetrySpec(1);
        }
    }
}
