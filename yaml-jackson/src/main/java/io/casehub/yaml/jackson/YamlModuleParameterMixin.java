package io.casehub.yaml.jackson;

import com.fasterxml.jackson.annotation.JsonProperty;

abstract class YamlModuleParameterMixin {
    @JsonProperty("default")
    abstract String defaultValue();
}
