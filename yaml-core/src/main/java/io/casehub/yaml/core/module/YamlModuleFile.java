package io.casehub.yaml.core.module;

import java.util.List;
import java.util.Map;

public record YamlModuleFile(
        YamlModuleHeader module,
        Map<String, Map<String, Object>> sections,
        List<YamlImport> imports) {

    public YamlModuleFile {
        if (sections == null) { sections = Map.of(); }
        if (imports == null) { imports = List.of(); }
    }

    public YamlModule toModule() {
        return new YamlModule(module.name(), module.parameters(), sections);
    }

    public record YamlModuleHeader(String name,
            Map<String, YamlModuleParameter> parameters) {
        public YamlModuleHeader {
            if (parameters == null) { parameters = Map.of(); }
        }
    }
}
