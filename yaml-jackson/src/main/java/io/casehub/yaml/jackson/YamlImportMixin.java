package io.casehub.yaml.jackson;

import com.fasterxml.jackson.annotation.JsonProperty;

abstract class YamlImportMixin {

    @JsonProperty("if")
    String condition;
}
