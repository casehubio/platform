package io.casehub.yaml.jackson;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.casehub.yaml.core.module.YamlModuleOutput;
import io.casehub.yaml.core.module.YamlModuleParameter;

import java.util.Map;

abstract class YamlModuleHeaderMixin {
    @JsonCreator
    YamlModuleHeaderMixin(
            @JsonProperty("name") String name,
            @JsonProperty("parameters") Map<String, YamlModuleParameter> parameters,
            @JsonProperty("outputs") Map<String, YamlModuleOutput> outputs,
            @JsonProperty("extends") String extendsModule) {}
}
