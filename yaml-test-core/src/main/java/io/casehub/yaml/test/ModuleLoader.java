package io.casehub.yaml.test;

import io.casehub.yaml.core.module.YamlImport;
import io.casehub.yaml.core.module.YamlModule;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public interface ModuleLoader {
    Map<String, YamlModule> loadModules(List<YamlImport> imports, Path playbookPath);
}
