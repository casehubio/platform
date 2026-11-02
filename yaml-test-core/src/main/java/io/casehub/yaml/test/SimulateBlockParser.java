package io.casehub.yaml.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.casehub.platform.simulation.InvocationRecord;
import io.casehub.platform.simulation.MapSimulationConfig;
import io.casehub.platform.simulation.SimulationConfig;
import io.casehub.platform.simulation.SimulationCorpus;
import io.casehub.platform.simulation.inmem.InMemorySimulationCorpus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SimulateBlockParser {

    private static final Map<String, AliasConfig> ALIASES = Map.of(
            "agent", new AliasConfig("agent-provider.invoke", "key", "field:user-prompt",
                    Map.of("prompt", "key", "response", "output"))
    );

    private SimulateBlockParser() {}

    public static ParsedSimulation parse(Map<String, Object> simulateBlock) {
        return parse(simulateBlock, null);
    }

    @SuppressWarnings("unchecked")
    public static ParsedSimulation parse(Map<String, Object> simulateBlock, Path resourceRoot) {
        var configBuilder = MapSimulationConfig.builder();
        var corpus = new InMemorySimulationCorpus<Object, Object>();

        for (var entry : simulateBlock.entrySet()) {
            String rawName = entry.getKey();
            AliasConfig alias = ALIASES.get(rawName);
            String qualifiedName = alias != null ? alias.qualifiedName : rawName;
            String defaultStrategy = alias != null ? alias.defaultStrategy : "key";
            Map<String, String> fieldMapping = alias != null ? alias.fieldMapping : Map.of();

            Object value = entry.getValue();
            String strategy = defaultStrategy;
            List<Map<String, Object>> corpusEntries = new ArrayList<>();

            if (value instanceof List<?> list) {
                for (var item : list) {
                    corpusEntries.add(normaliseEntry((Map<String, Object>) item, fieldMapping));
                }
            } else if (value instanceof Map<?, ?> map) {
                var configMap = (Map<String, Object>) map;

                if (configMap.containsKey("strategy")) {
                    strategy = (String) configMap.get("strategy");
                }

                if (configMap.containsKey("corpus-file") && resourceRoot != null) {
                    var fileEntries = loadCorpusFile(
                            resourceRoot.resolve((String) configMap.get("corpus-file")), fieldMapping);
                    corpusEntries.addAll(fileEntries);
                }

                if (configMap.containsKey("corpus")) {
                    for (var item : (List<Map<String, Object>>) configMap.get("corpus")) {
                        corpusEntries.add(normaliseEntry(item, fieldMapping));
                    }
                }
            }

            configBuilder.strategy(qualifiedName, strategy);

            List<InvocationRecord<Object, Object>> records = new ArrayList<>();
            for (var ce : corpusEntries) {
                String key = ce.containsKey("key") ? String.valueOf(ce.get("key")) : null;
                Object output = ce.get("output");
                records.add(InvocationRecord.of("default", key, key, output));
            }
            if (!records.isEmpty()) {
                corpus.seed(qualifiedName, records);
            }
        }

        return new ParsedSimulation(configBuilder.build(), corpus);
    }

    private static Map<String, Object> normaliseEntry(
            Map<String, Object> entry, Map<String, String> fieldMapping) {
        var normalised = new LinkedHashMap<String, Object>();
        for (var e : entry.entrySet()) {
            String mapped = fieldMapping.getOrDefault(e.getKey(), e.getKey());
            normalised.put(mapped, e.getValue());
        }
        return normalised;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> loadCorpusFile(
            Path file, Map<String, String> fieldMapping) {
        try {
            var mapper = new ObjectMapper(new YAMLFactory());
            List<Map<String, Object>> raw = mapper.readValue(
                    Files.newInputStream(file),
                    mapper.getTypeFactory().constructCollectionType(List.class, Map.class));
            List<Map<String, Object>> result = new ArrayList<>();
            for (var entry : raw) {
                result.add(normaliseEntry(entry, fieldMapping));
            }
            return result;
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to load corpus file: " + file, e);
        }
    }

    public record ParsedSimulation(
            SimulationConfig config,
            SimulationCorpus<Object, Object> corpus) {}

    private record AliasConfig(
            String qualifiedName,
            String defaultStrategy,
            String defaultKeyExtractor,
            Map<String, String> fieldMapping) {}
}
