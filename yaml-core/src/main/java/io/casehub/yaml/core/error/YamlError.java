package io.casehub.yaml.core.error;

import java.util.Map;

public sealed interface YamlError permits ParseError, RuntimeStepError, CoordinationError {
    String stepName();
    ErrorCategory category();
    String summary();
    SourceLocation location();
    Throwable cause();
    default Map<String, Object> toDetailMap() { return Map.of(); }
}
