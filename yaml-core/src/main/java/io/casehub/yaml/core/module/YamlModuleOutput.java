package io.casehub.yaml.core.module;

import java.util.Objects;

public record YamlModuleOutput(ParameterType type, String value) {

    public YamlModuleOutput {
        if (type == null) {type = ParameterType.STRING;}
        Objects.requireNonNull(value, "Output value template is required");
    }
}
