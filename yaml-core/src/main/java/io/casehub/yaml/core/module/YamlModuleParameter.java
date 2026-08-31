package io.casehub.yaml.core.module;

public record YamlModuleParameter(
        ParameterType type,
        boolean required,
        String defaultValue,
        Integer minLength,
        Integer maxLength,
        String pattern,
        Number minimum,
        Number maximum) {

    public YamlModuleParameter {
        if (type == null) { type = ParameterType.STRING; }
    }
}
