package io.casehub.yaml.jackson;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import io.casehub.yaml.core.module.YamlImport;
import io.casehub.yaml.core.module.YamlModuleFile;
import io.casehub.yaml.core.module.YamlModuleFile.YamlModuleHeader;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@JsonPOJOBuilder(withPrefix = "")
public class YamlModuleFileBuilder {

    private YamlModuleHeader module;
    private List<YamlImport> imports = new ArrayList<>();
    private final Map<String, Map<String, Object>> sections = new LinkedHashMap<>();

    @JsonProperty("module")
    public YamlModuleFileBuilder module(YamlModuleHeader module) {
        this.module = module;
        return this;
    }

    @JsonProperty("imports")
    public YamlModuleFileBuilder imports(List<YamlImport> imports) {
        this.imports = imports != null ? imports : new ArrayList<>();
        return this;
    }

    @JsonAnySetter
    @SuppressWarnings("unchecked")
    public void addSection(String name, Object value) {
        if (value instanceof Map) {
            sections.put(name, (Map<String, Object>) value);
        }
    }

    public YamlModuleFile build() {
        return new YamlModuleFile(module, Map.copyOf(sections), List.copyOf(imports));
    }
}
