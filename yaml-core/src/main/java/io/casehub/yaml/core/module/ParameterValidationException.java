package io.casehub.yaml.core.module;

import java.util.List;

public class ParameterValidationException extends RuntimeException {

    private final List<ParameterViolation> violations;

    public ParameterValidationException(List<ParameterViolation> violations) {
        super("Parameter validation failed with " + violations.size() + " violation(s): "
              + violations.stream()
                    .map(v -> v.parameterName() + " (" + v.constraint() + ")")
                    .reduce((a, b) -> a + ", " + b).orElse(""));
        this.violations = List.copyOf(violations);
    }

    public List<ParameterViolation> violations() { return violations; }
}
