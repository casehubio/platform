package io.casehub.yaml.jackson;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import io.casehub.yaml.core.step.StepDefinition;
import io.casehub.yaml.core.step.StepDefinitionFile;
import io.casehub.yaml.core.step.StepDefinitionParser;

import java.util.LinkedHashMap;
import java.util.Map;

@JsonPOJOBuilder(withPrefix = "")
public class StepDefinitionFileBuilder {

    private String namespace = "";
    private final Map<String, StepDefinition> actions = new LinkedHashMap<>();

    @JsonProperty("namespace")
    public StepDefinitionFileBuilder namespace(String namespace) {
        this.namespace = namespace != null ? namespace : "";
        return this;
    }

    @JsonAnySetter
    @SuppressWarnings("unchecked")
    public void addAction(String name, Object value) {
        if (value instanceof Map) {
            actions.put(name, StepDefinitionParser.parseAction(name, (Map<String, Object>) value));
        }
    }

    public StepDefinitionFile build() {
        return new StepDefinitionFile(namespace, actions);
    }
}
