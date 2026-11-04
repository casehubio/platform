package io.casehub.yaml.core.error;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public enum ErrorCategory {
    UNKNOWN_STEP("unknown-step"),
    PARAMETER_VIOLATION("parameter-violation"),
    INVALID_STRUCTURE("invalid-structure"),
    EXPRESSION_PARSE("expression-parse"),
    DUPLICATE_STEP("duplicate-step"),
    STEP_ACTION_FAILED("action-failed"),
    TIMEOUT("timeout"),
    RETRY_EXHAUSTED("retry-exhausted"),
    GUARD_REJECTED("guard-rejected"),
    EXPRESSION_EVAL("expression-eval"),
    CHANNEL_CLOSED("channel-closed"),
    DEADLINE_EXCEEDED("deadline-exceeded"),
    CORRELATION_TIMEOUT("correlation-timeout"),
    SEMAPHORE_REENTRANCY("semaphore-reentrancy"),
    ILLEGAL_TRANSITION("illegal-transition"),
    RATE_LIMIT("rate-limit"),
    SESSION_LIMIT("session-limit"),
    NOT_FOUND("not-found"),
    AUTH_DENIED("auth-denied"),
    SIMULATION("simulation"),
    INFRASTRUCTURE("infrastructure");

    private static final Map<String, ErrorCategory> BY_YAML_KEY =
            Stream.of(values()).collect(Collectors.toUnmodifiableMap(ErrorCategory::yamlKey, e -> e));

    private final String yamlKey;

    ErrorCategory(String yamlKey) {
        this.yamlKey = yamlKey;
    }

    public String yamlKey() { return yamlKey; }

    public static ErrorCategory fromYamlKey(String key) {
        return key != null ? BY_YAML_KEY.get(key) : null;
    }
}
