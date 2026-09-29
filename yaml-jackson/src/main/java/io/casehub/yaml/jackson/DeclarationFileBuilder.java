package io.casehub.yaml.jackson;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import io.casehub.yaml.core.step.Declaration;
import io.casehub.yaml.core.step.DeclarationFile;
import io.casehub.yaml.core.step.DeclarationParser;

import java.util.LinkedHashMap;
import java.util.Map;

@JsonPOJOBuilder(withPrefix = "")
public class DeclarationFileBuilder {

    private String namespace = "";
    private final Map<String, Declaration> actions = new LinkedHashMap<>();

    @JsonProperty("namespace")
    public DeclarationFileBuilder namespace(String namespace) {
        this.namespace = namespace != null ? namespace : "";
        return this;
    }

    @JsonAnySetter
    @SuppressWarnings("unchecked")
    public void addAction(String name, Object value) {
        if (value instanceof Map) {
            actions.put(name, DeclarationParser.parseAction(name, (Map<String, Object>) value));
        }
    }

    public DeclarationFile build() {
        return new DeclarationFile(namespace, actions);
    }
}
