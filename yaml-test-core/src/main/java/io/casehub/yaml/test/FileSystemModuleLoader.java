package io.casehub.yaml.test;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.casehub.yaml.core.module.ModuleExpander;
import io.casehub.yaml.core.module.YamlImport;
import io.casehub.yaml.core.module.YamlModule;
import io.casehub.yaml.core.module.YamlModuleFile;
import io.casehub.yaml.jackson.YamlCoreJacksonModule;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class FileSystemModuleLoader implements ModuleLoader {

    private static final String CLASSPATH_PREFIX = "META-INF/playbook-modules/";
    private final ObjectMapper mapper;

    public FileSystemModuleLoader() {
        this.mapper = new ObjectMapper(new YAMLFactory())
                .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
                .registerModule(new YamlCoreJacksonModule());
    }

    @Override
    public Map<String, YamlModule> loadModules(List<YamlImport> imports, Path playbookPath) {
        List<YamlImport> moduleImports = imports.stream()
                .filter(imp -> imp.steps() == null)
                .toList();

        if (moduleImports.isEmpty()) {
            return Map.of();
        }

        Path moduleDir = playbookPath.getParent().resolve("modules");
        List<YamlModuleFile> moduleFiles = new ArrayList<>();

        for (YamlImport imp : moduleImports) {
            YamlModuleFile file = loadModuleFile(imp.module(), moduleDir);
            moduleFiles.add(file);
        }

        return ModuleExpander.resolveExtensions(moduleFiles);
    }

    private YamlModuleFile loadModuleFile(String moduleName, Path moduleDir) {
        Path relativePath = moduleDir.resolve(moduleName + ".yaml");
        if (Files.exists(relativePath)) {
            return parseModuleFile(relativePath);
        }

        String classpathResource = CLASSPATH_PREFIX + moduleName + ".yaml";
        InputStream is = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(classpathResource);
        if (is != null) {
            try (is) {
                return mapper.readValue(is, YamlModuleFile.class);
            } catch (IOException e) {
                throw new IllegalArgumentException(
                        "Failed to parse module from classpath: " + classpathResource, e);
            }
        }

        throw new ModuleNotFoundException(moduleName, List.of(
                relativePath.toString(),
                "classpath:" + classpathResource));
    }

    private YamlModuleFile parseModuleFile(Path path) {
        try (InputStream is = Files.newInputStream(path)) {
            return mapper.readValue(is, YamlModuleFile.class);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to parse module: " + path, e);
        }
    }
}
