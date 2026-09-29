package io.casehub.yaml.plugin.api;

import java.util.List;

public record Parameter(
        ParameterType type,
        boolean required,
        String defaultValue,
        List<String> allowedValues,
        String format,
        String description) {

    public Parameter {
        if (type == null) type = ParameterType.STRING;
        if (allowedValues == null) allowedValues = List.of();
        if (defaultValue != null && !type.isScalar()) {
            throw new IllegalArgumentException(
                    "Default values are only supported for scalar types, not " + type);
        }
    }
}
