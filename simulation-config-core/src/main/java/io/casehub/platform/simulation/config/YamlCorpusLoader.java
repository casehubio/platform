package io.casehub.platform.simulation.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.casehub.platform.simulation.InvocationRecord;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class YamlCorpusLoader {

    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    private final String defaultTenancyId;

    public YamlCorpusLoader() {
        this(null);
    }

    public YamlCorpusLoader(String defaultTenancyId) {
        this.defaultTenancyId = defaultTenancyId;
    }

    @SuppressWarnings("unchecked")
    public Map<String, List<InvocationRecord<Object, Object>>> load(InputStream input) {
        try {
            Map<String, List<Map<String, Object>>> raw =
                    yamlMapper.readValue(input, Map.class);
            Map<String, List<InvocationRecord<Object, Object>>> result = new HashMap<>();
            raw.forEach((qualifiedName, entries) -> {
                List<InvocationRecord<Object, Object>> records = new ArrayList<>();
                for (Map<String, Object> entry : entries) {
                    String tenancyId = (String) entry.get("tenancy-id");
                    if (tenancyId == null) {
                        tenancyId = defaultTenancyId;
                    }
                    records.add(new InvocationRecord<>(
                            tenancyId,
                            (String) entry.get("key"),
                            entry.get("input"),
                            entry.get("output"),
                            Instant.now()));
                }
                result.put(qualifiedName, records);
            });
            return result;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse YAML corpus", e);
        }
    }

    public Map<String, List<InvocationRecord<Object, Object>>> loadFromPaths(
            List<String> paths) {
        Map<String, List<InvocationRecord<Object, Object>>> merged = new HashMap<>();
        for (String path : paths) {
            InputStream is = openStream(path.trim());
            Map<String, List<InvocationRecord<Object, Object>>> loaded = load(is);
            loaded.forEach((qn, records) ->
                    merged.computeIfAbsent(qn, k -> new ArrayList<>()).addAll(records));
        }
        return merged;
    }

    private InputStream openStream(String path) {
        if (path.startsWith("classpath:")) {
            String resource = path.substring("classpath:".length());
            InputStream is = Thread.currentThread().getContextClassLoader()
                    .getResourceAsStream(resource);
            if (is == null) {
                throw new IllegalArgumentException(
                        "Corpus file not found on classpath: " + resource);
            }
            return is;
        }
        try {
            return java.nio.file.Files.newInputStream(java.nio.file.Path.of(path));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to open corpus file: " + path, e);
        }
    }
}
