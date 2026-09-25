package io.casehub.yaml.core.step;

import java.util.List;

public record StepParameter(
        StepParameterType type,
        boolean required,
        String defaultValue,
        List<String> allowedValues,
        String format,
        String description) {

    public StepParameter {
        if (type == null) type = StepParameterType.STRING;
        if (allowedValues == null) allowedValues = List.of();
        if (defaultValue != null && !type.isScalar()) {
            throw new IllegalArgumentException(
                    "Default values are only supported for scalar types, not " + type);
        }
    }
}
