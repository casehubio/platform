package io.casehub.yaml.step.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.platform.api.process.ProcessExecutor;
import io.casehub.yaml.core.step.InvokeBinding;
import io.casehub.yaml.core.step.StepDefinition;
import io.casehub.yaml.core.step.StepParameter;
import io.casehub.yaml.core.step.StepParameterType;
import io.casehub.yaml.step.CatalogEntry;
import io.casehub.yaml.step.CatalogSource;
import io.casehub.yaml.step.handler.ScriptInvokeHandler;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ScriptSource implements CatalogSource {

    private static final Map<String, String> EXTENSION_RUNTIMES = Map.of(
            ".py", "python3",
            ".js", "node",
            ".mjs", "node"
    );

    private final List<Path> paths;
    private final ObjectMapper yamlMapper;
    private final ScriptInvokeHandler handler;

    public ScriptSource(List<Path> paths, ObjectMapper yamlMapper, ProcessExecutor processExecutor) {
        this.paths = List.copyOf(paths);
        this.yamlMapper = yamlMapper;
        this.handler = new ScriptInvokeHandler(new ObjectMapper(), processExecutor);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void populate(Map<String, CatalogEntry> entries) {
        for (Path dir : paths) {
            if (!Files.isDirectory(dir)) continue;
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
                for (Path file : stream) {
                    String fileName = file.getFileName().toString();
                    String runtime = runtimeForFile(fileName);
                    if (runtime == null) continue;

                    String baseName = baseName(fileName);
                    Path schemaPath = dir.resolve(baseName + ".schema.yaml");
                    if (!Files.exists(schemaPath)) continue;

                    if (entries.containsKey(baseName)) continue;

                    Map<String, Object> schema = yamlMapper.readValue(
                            schemaPath.toFile(), LinkedHashMap.class);

                    String name = schema.containsKey("name")
                            ? (String) schema.get("name") : baseName;
                    String description = (String) schema.get("description");
                    String timeout = (String) schema.get("timeout");

                    Map<String, StepParameter> inputs = parseParams(
                            (Map<String, Object>) schema.get("inputs"));
                    Map<String, StepParameter> outputs = parseParams(
                            (Map<String, Object>) schema.get("outputs"));

                    InvokeBinding.Script binding = new InvokeBinding.Script(
                            runtime, file.toAbsolutePath().toString(), timeout, null, null);
                    StepDefinition def = new StepDefinition(name, description, inputs, outputs, binding);

                    entries.put(name, new CatalogEntry(name, def,
                            handler.create(def, binding)));
                }
            } catch (IOException e) {
                throw new IllegalStateException("Failed to scan script directory: " + dir, e);
            }
        }
    }

    @Override
    public int priority() {
        return 400;
    }

    @SuppressWarnings("unchecked")
    private Map<String, StepParameter> parseParams(Map<String, Object> raw) {
        if (raw == null) return Map.of();
        Map<String, StepParameter> result = new LinkedHashMap<>();
        for (var entry : raw.entrySet()) {
            Map<String, Object> paramMap = (Map<String, Object>) entry.getValue();
            String typeStr = (String) paramMap.getOrDefault("type", "string");
            boolean required = Boolean.TRUE.equals(paramMap.get("required"));
            String desc = (String) paramMap.get("description");
            result.put(entry.getKey(), new StepParameter(
                    StepParameterType.fromString(typeStr), required, null, null, null, desc));
        }
        return result;
    }

    private static String runtimeForFile(String fileName) {
        for (var entry : EXTENSION_RUNTIMES.entrySet()) {
            if (fileName.endsWith(entry.getKey())) return entry.getValue();
        }
        return null;
    }

    private static String baseName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }
}
