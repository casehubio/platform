package io.casehub.platform.simulation.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.casehub.platform.simulation.ExhaustionPolicy;
import io.casehub.platform.simulation.InvocationRecord;
import io.casehub.platform.simulation.ProfileSource;
import io.casehub.platform.simulation.SimulationConfig;
import io.casehub.platform.simulation.SimulationProfile;
import io.casehub.platform.simulation.inmem.InMemorySimulationCorpus;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class YamlSimulationConfig implements SimulationConfig, ProfileSource {

    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    private final String defaultTenancyId;
    private final Map<String, MethodConfig> methods;
    private final Map<String, ProfileConfig> profiles;

    public YamlSimulationConfig(InputStream yamlInput) {
        this(yamlInput, null);
    }

    @SuppressWarnings("unchecked")
    public YamlSimulationConfig(InputStream yamlInput, String defaultTenancyIdOverride) {
        try {
            Map<String, Object> root;
            try {
                root = YAML_MAPPER.readValue(yamlInput, Map.class);
            } catch (com.fasterxml.jackson.databind.exc.MismatchedInputException e) {
                root = null;
            }
            if (root == null) {
                root = Map.of();
            }

            String yamlTenancy = (String) root.get("default-tenancy-id");
            this.defaultTenancyId = defaultTenancyIdOverride != null
                    ? defaultTenancyIdOverride : yamlTenancy;

            this.methods = parseMethods(
                    (Map<String, Map<String, Object>>) root.get("methods"));

            this.profiles = parseProfiles(
                    (Map<String, Map<String, Object>>) root.get("profiles"));

        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse simulation YAML", e);
        }
    }

    @Override
    public Optional<String> strategyFor(String qualifiedName) {
        return Optional.ofNullable(methods.get(qualifiedName))
                .map(MethodConfig::strategy);
    }

    @Override
    public boolean captureEnabled(String qualifiedName) {
        return Optional.ofNullable(methods.get(qualifiedName))
                .map(MethodConfig::capture)
                .orElse(false);
    }

    @Override
    public Optional<ExhaustionPolicy> exhaustionPolicy(String qualifiedName) {
        return Optional.ofNullable(methods.get(qualifiedName))
                .map(MethodConfig::exhaustionPolicy);
    }

    @Override
    public Optional<Double> threshold(String qualifiedName) {
        return Optional.ofNullable(methods.get(qualifiedName))
                .map(MethodConfig::threshold);
    }

    public Map<String, String> extractorSpecs() {
        return methods.entrySet().stream()
                .filter(e -> e.getValue().keyExtractor() != null)
                .collect(Collectors.toMap(Map.Entry::getKey,
                        e -> e.getValue().keyExtractor()));
    }

    public Map<String, String> scorerSpecs() {
        return methods.entrySet().stream()
                .filter(e -> e.getValue().scorer() != null)
                .collect(Collectors.toMap(Map.Entry::getKey,
                        e -> e.getValue().scorer()));
    }

    public Optional<String> defaultTenancyId() {
        return Optional.ofNullable(defaultTenancyId);
    }

    public Set<String> profileNames() {
        return Collections.unmodifiableSet(profiles.keySet());
    }

    public Map<String, List<InvocationRecord<Object, Object>>> loadAllCorpus() {
        Map<String, List<InvocationRecord<Object, Object>>> result = new HashMap<>();
        methods.forEach((qn, mc) -> {
            List<InvocationRecord<Object, Object>> records = loadCorpusForMethod(qn, mc);
            if (!records.isEmpty()) {
                result.put(qn, records);
            }
        });
        return result;
    }

    public Map<String, List<InvocationRecord<Object, Object>>> loadAllCorpus(
            String activeProfile) {
        Map<String, List<InvocationRecord<Object, Object>>> result = loadAllCorpus();

        ProfileConfig profile = profiles.get(activeProfile);
        if (profile == null) {
            return result;
        }

        profile.methods().forEach((qn, mc) -> {
            List<InvocationRecord<Object, Object>> profileRecords =
                    loadCorpusForMethod(qn, mc);
            if (!profileRecords.isEmpty()) {
                result.computeIfAbsent(qn, k -> new ArrayList<>())
                        .addAll(profileRecords);
            }
        });

        if (profile.corpusFiles() != null && !profile.corpusFiles().isEmpty()) {
            loadExternalCorpusFiles(profile.corpusFiles())
                    .forEach((qn, records) ->
                            result.computeIfAbsent(qn, k -> new ArrayList<>())
                                    .addAll(records));
        }

        return result;
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public Optional<SimulationProfile> resolve(String name) {
        ProfileConfig profile = profiles.get(name);
        if (profile == null) {
            return Optional.empty();
        }

        SimulationConfig composedConfig = new SimulationConfig() {
            @Override
            public Optional<String> strategyFor(String qualifiedName) {
                MethodConfig pm = profile.methods().get(qualifiedName);
                if (pm != null && pm.strategy() != null) {
                    return Optional.of(pm.strategy());
                }
                return YamlSimulationConfig.this.strategyFor(qualifiedName);
            }

            @Override
            public boolean captureEnabled(String qualifiedName) {
                MethodConfig pm = profile.methods().get(qualifiedName);
                if (pm != null && pm.capture()) {
                    return true;
                }
                return YamlSimulationConfig.this.captureEnabled(qualifiedName);
            }

            @Override
            public Optional<ExhaustionPolicy> exhaustionPolicy(String qualifiedName) {
                MethodConfig pm = profile.methods().get(qualifiedName);
                if (pm != null && pm.exhaustionPolicy() != null) {
                    return Optional.of(pm.exhaustionPolicy());
                }
                return YamlSimulationConfig.this.exhaustionPolicy(qualifiedName);
            }

            @Override
            public Optional<Double> threshold(String qualifiedName) {
                MethodConfig pm = profile.methods().get(qualifiedName);
                if (pm != null && pm.threshold() != null) {
                    return Optional.of(pm.threshold());
                }
                return YamlSimulationConfig.this.threshold(qualifiedName);
            }
        };

        InMemorySimulationCorpus corpus = new InMemorySimulationCorpus<>();
        profile.methods().keySet().forEach(qn -> {
            MethodConfig baseMc = methods.get(qn);
            if (baseMc != null) {
                List<InvocationRecord<Object, Object>> baseRecords =
                        loadCorpusForMethod(qn, baseMc);
                if (!baseRecords.isEmpty()) {
                    corpus.seed(qn, baseRecords);
                }
            }
        });
        profile.methods().forEach((qn, mc) -> {
            List<InvocationRecord<Object, Object>> profileRecords =
                    loadCorpusForMethod(qn, mc);
            if (!profileRecords.isEmpty()) {
                corpus.seed(qn, profileRecords);
            }
        });
        if (profile.corpusFiles() != null && !profile.corpusFiles().isEmpty()) {
            loadExternalCorpusFiles(profile.corpusFiles())
                    .forEach(corpus::seed);
        }

        return Optional.of(new SimulationProfile(composedConfig, corpus));
    }

    @SuppressWarnings("unchecked")
    private Map<String, MethodConfig> parseMethods(Map<String, Map<String, Object>> raw) {
        if (raw == null) {
            return Map.of();
        }
        Map<String, MethodConfig> result = new LinkedHashMap<>();
        raw.forEach((qn, props) -> result.put(qn, parseMethodConfig(props)));
        return result;
    }

    @SuppressWarnings("unchecked")
    private MethodConfig parseMethodConfig(Map<String, Object> props) {
        String strategy = (String) props.get("strategy");
        boolean capture = Boolean.TRUE.equals(props.get("capture"));
        ExhaustionPolicy ep = props.containsKey("exhaustion-policy")
                ? ExhaustionPolicy.valueOf(
                ((String) props.get("exhaustion-policy")).toUpperCase().replace("-", "_"))
                : null;
        String keyExtractor = (String) props.get("key-extractor");
        String scorer = (String) props.get("scorer");
        Double threshold = props.containsKey("threshold")
                ? ((Number) props.get("threshold")).doubleValue() : null;

        List<CorpusEntry> corpus = new ArrayList<>();
        List<Map<String, Object>> rawCorpus =
                (List<Map<String, Object>>) props.get("corpus");
        if (rawCorpus != null) {
            for (Map<String, Object> entry : rawCorpus) {
                if (!entry.containsKey("output")) {
                    throw new IllegalArgumentException(
                            "Corpus entry missing required 'output' field");
                }
                corpus.add(new CorpusEntry(
                        (String) entry.get("key"),
                        (String) entry.get("tenancy-id"),
                        entry.get("input"),
                        entry.get("output")));
            }
        }

        List<String> corpusFiles = (List<String>) props.get("corpus-files");

        return new MethodConfig(strategy, capture, ep, keyExtractor, scorer,
                threshold, corpus, corpusFiles);
    }

    @SuppressWarnings("unchecked")
    private Map<String, ProfileConfig> parseProfiles(
            Map<String, Map<String, Object>> raw) {
        if (raw == null) {
            return Map.of();
        }
        Map<String, ProfileConfig> result = new LinkedHashMap<>();
        raw.forEach((name, props) -> {
            Map<String, MethodConfig> profileMethods = parseMethods(
                    (Map<String, Map<String, Object>>) props.get("methods"));
            List<String> corpusFiles = (List<String>) props.get("corpus-files");
            result.put(name, new ProfileConfig(profileMethods, corpusFiles));
        });
        return result;
    }

    private List<InvocationRecord<Object, Object>> loadCorpusForMethod(
            String qualifiedName, MethodConfig mc) {
        List<InvocationRecord<Object, Object>> records = new ArrayList<>();

        if (mc.corpus() != null) {
            for (CorpusEntry entry : mc.corpus()) {
                String tenancyId = entry.tenancyId() != null
                        ? entry.tenancyId() : defaultTenancyId;
                records.add(new InvocationRecord<>(
                        tenancyId, entry.key(), entry.input(),
                        entry.output(), Instant.now()));
            }
        }

        if (mc.corpusFiles() != null && !mc.corpusFiles().isEmpty()) {
            Map<String, List<InvocationRecord<Object, Object>>> external =
                    loadExternalCorpusFiles(mc.corpusFiles());
            List<InvocationRecord<Object, Object>> forMethod =
                    external.get(qualifiedName);
            if (forMethod != null) {
                records.addAll(forMethod);
            }
        }

        return records;
    }

    @SuppressWarnings("unchecked")
    private Map<String, List<InvocationRecord<Object, Object>>> loadExternalCorpusFiles(
            List<String> paths) {
        Map<String, List<InvocationRecord<Object, Object>>> merged = new HashMap<>();
        for (String path : paths) {
            try (InputStream is = openStream(path.trim())) {
                Map<String, List<Map<String, Object>>> raw =
                        YAML_MAPPER.readValue(is, Map.class);
                if (raw == null) continue;
                raw.forEach((qn, entries) -> {
                    List<InvocationRecord<Object, Object>> records = new ArrayList<>();
                    for (Map<String, Object> entry : entries) {
                        String tenancyId = (String) entry.get("tenancy-id");
                        if (tenancyId == null) {
                            tenancyId = defaultTenancyId;
                        }
                        records.add(new InvocationRecord<>(
                                tenancyId, (String) entry.get("key"),
                                entry.get("input"), entry.get("output"),
                                Instant.now()));
                    }
                    merged.computeIfAbsent(qn, k -> new ArrayList<>()).addAll(records);
                });
            } catch (IOException e) {
                throw new UncheckedIOException(
                        "Failed to load corpus file: " + path, e);
            }
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
            throw new UncheckedIOException(
                    "Failed to open corpus file: " + path, e);
        }
    }

    record MethodConfig(
            String strategy,
            boolean capture,
            ExhaustionPolicy exhaustionPolicy,
            String keyExtractor,
            String scorer,
            Double threshold,
            List<CorpusEntry> corpus,
            List<String> corpusFiles) {}

    record CorpusEntry(
            String key,
            String tenancyId,
            Object input,
            Object output) {}

    record ProfileConfig(
            Map<String, MethodConfig> methods,
            List<String> corpusFiles) {}
}
