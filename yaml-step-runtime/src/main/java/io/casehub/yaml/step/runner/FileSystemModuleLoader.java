package io.casehub.yaml.step.runner;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.casehub.yaml.core.error.LocationIndex;
import io.casehub.yaml.core.module.ModuleExpander;
import io.casehub.yaml.core.module.YamlImport;
import io.casehub.yaml.core.module.YamlModule;
import io.casehub.yaml.core.module.YamlModuleFile;
import io.casehub.yaml.jackson.LocationTrackingModule;
import io.casehub.yaml.jackson.YamlCoreJacksonModule;

import com.fasterxml.jackson.databind.ObjectReader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class FileSystemModuleLoader implements ModuleLoader {

    private static final String CLASSPATH_PREFIX = "META-INF/playbook-modules/";
    private final ObjectMapper mapper;

    public FileSystemModuleLoader() {
        this.mapper = new ObjectMapper(new YAMLFactory())
                .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
                .registerModule(new YamlCoreJacksonModule())
                .registerModule(new LocationTrackingModule());
    }

    @Override
    public Map<String, YamlModule> loadModules(List<YamlImport> imports, Path playbookPath) {
        return loadModules(imports, playbookPath, null);
    }

    @Override
    public Map<String, YamlModule> loadModules(List<YamlImport> imports, Path playbookPath,
                                               LocationIndex locations) {
        List<YamlImport> moduleImports = imports.stream()
                                                .filter(imp -> imp.steps() == null)
                                                .toList();

        if (moduleImports.isEmpty()) {
            return Map.of();
        }

        Path                        moduleDir   = playbookPath.getParent().resolve("modules");
        Map<String, YamlModuleFile> uniqueFiles = new LinkedHashMap<>();

        for (YamlImport imp : moduleImports) {
            uniqueFiles.computeIfAbsent(imp.module(),
                                        name -> loadModuleFile(name, moduleDir, locations));
        }

        return ModuleExpander.resolveExtensions(new ArrayList<>(uniqueFiles.values()));
    }

    private YamlModuleFile loadModuleFile(String moduleName, Path moduleDir,
                                          LocationIndex locations) {
        if (moduleName.contains("..") || moduleName.startsWith("/") || moduleName.startsWith("\\")) {
            throw new IllegalArgumentException(
                    "Module name '" + moduleName + "' contains path traversal characters");
        }

        Path relativePath = moduleDir.resolve(moduleName + ".yaml").normalize();
        if (!relativePath.startsWith(moduleDir.normalize())) {
            throw new IllegalArgumentException(
                    "Module name '" + moduleName + "' resolves outside module directory");
        }

        if (Files.exists(relativePath)) {
            try {
                String yaml = Files.readString(relativePath);
                return parseModuleContent(yaml, relativePath.toString(), locations);
            } catch (IOException e) {
                throw new IllegalArgumentException("Failed to parse module: " + relativePath, e);
            }
        }

        String classpathResource = CLASSPATH_PREFIX + moduleName + ".yaml";
        InputStream is = Thread.currentThread().getContextClassLoader()
                               .getResourceAsStream(classpathResource);
        if (is != null) {
            try (is) {
                String yaml = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                return parseModuleContent(yaml, "classpath:" + classpathResource, locations);
            } catch (IOException e) {
                throw new IllegalArgumentException(
                        "Failed to parse module from classpath: " + classpathResource, e);
            }
        }

        throw new ModuleNotFoundException(moduleName, List.of(
                relativePath.toString(),
                "classpath:" + classpathResource));
    }

    private YamlModuleFile parseModuleContent(String yaml, String filePath,
                                              LocationIndex locations) throws IOException {
        ObjectReader reader = mapper.readerFor(YamlModuleFile.class);
        if (locations != null) {
            reader = reader
                    .withAttribute(LocationTrackingModule.LOCATION_INDEX_KEY, locations)
                    .withAttribute(LocationTrackingModule.FILE_PATH_KEY, filePath);
        }
        return reader.readValue(yaml);
    }
}
