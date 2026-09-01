package io.casehub.yaml.core.module;

import java.util.List;

public record YamlModuleParameter(
        ParameterType type,
        boolean required,
        String defaultValue,
        Integer minLength,
        Integer maxLength,
        String pattern,
        Number minimum,
        Number maximum,
        List<String> allowedValues,
        String constraintDescription) {

    public YamlModuleParameter {
        if (type == null) { type = ParameterType.STRING; }
        if (allowedValues == null) { allowedValues = List.of(); }
    }
}
