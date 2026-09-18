package io.casehub.platform.simulation.config;

import io.casehub.platform.simulation.ExhaustionPolicy;
import io.casehub.platform.simulation.ProfileSource;
import io.casehub.platform.simulation.SimulationConfig;
import io.casehub.platform.simulation.SimulationProfile;
import io.casehub.platform.simulation.inmem.InMemorySimulationCorpus;
import org.eclipse.microprofile.config.Config;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class SmallRyeSimulationConfig implements SimulationConfig, ProfileSource {

    private static final String PREFIX = "casehub.simulation.";
    private static final String PROFILES_SEGMENT = "profiles.";
    private static final String ACTIVE_PROFILE_KEY = "casehub.simulation.active-profile";

    private final Map<String, MethodSimulationConfig> methods;
    private final Map<String, Map<String, MethodSimulationConfig>> profiles;
    private final Map<String, List<String>> profileCorpusFiles;
    private final String activeProfile;

    public SmallRyeSimulationConfig(Config config) {
        this.methods = new HashMap<>();
        this.profiles = new HashMap<>();
        this.profileCorpusFiles = new HashMap<>();
        this.activeProfile = config.getOptionalValue(ACTIVE_PROFILE_KEY, String.class).orElse(null);

        for (String name : config.getPropertyNames()) {
            if (!name.startsWith(PREFIX)) {
                continue;
            }
            String suffix = name.substring(PREFIX.length());

            if (suffix.startsWith(PROFILES_SEGMENT)) {
                parseProfileEntry(config, name, suffix.substring(PROFILES_SEGMENT.length()));
            } else if (suffix.equals("active-profile")) {
                continue;
            } else {
                parseFlatEntry(config, name, suffix);
            }
        }
    }

    private void parseFlatEntry(Config config, String name, String suffix) {
        String[] parts = suffix.split("\\.");
        if (parts.length != 3) {
            return;
        }
        String qualifiedName = parts[0] + "." + parts[1];
        String property = parts[2];
        config.getOptionalValue(name, String.class)
                .ifPresent(value -> methods
                        .computeIfAbsent(qualifiedName, k -> new MethodSimulationConfig())
                        .set(property, value));
    }

    private void parseProfileEntry(Config config, String name, String profileSuffix) {
        int firstDot = profileSuffix.indexOf('.');
        if (firstDot < 0) {
            return;
        }
        String profileName = profileSuffix.substring(0, firstDot);
        String remainder = profileSuffix.substring(firstDot + 1);

        if (remainder.equals("corpus.files")) {
            config.getOptionalValue(name, String.class)
                    .ifPresent(value -> profileCorpusFiles.put(profileName,
                            Arrays.asList(value.split(","))));
            return;
        }

        String[] parts = remainder.split("\\.");
        if (parts.length != 3) {
            return;
        }
        String qualifiedName = parts[0] + "." + parts[1];
        String property = parts[2];
        config.getOptionalValue(name, String.class)
                .ifPresent(value -> profiles
                        .computeIfAbsent(profileName, k -> new HashMap<>())
                        .computeIfAbsent(qualifiedName, k -> new MethodSimulationConfig())
                        .set(property, value));
    }

    @Override
    public Optional<String> strategyFor(String qualifiedName) {
        if (activeProfile != null) {
            var profileMethods = profiles.get(activeProfile);
            if (profileMethods != null) {
                var profileMethod = profileMethods.get(qualifiedName);
                if (profileMethod != null) {
                    var result = profileMethod.strategy();
                    if (result.isPresent()) {
                        return result;
                    }
                }
            }
        }
        return Optional.ofNullable(methods.get(qualifiedName))
                       .flatMap(MethodSimulationConfig::strategy);
    }

    @Override
    public boolean captureEnabled(String qualifiedName) {
        if (activeProfile != null) {
            var profileMethods = profiles.get(activeProfile);
            if (profileMethods != null) {
                var profileMethod = profileMethods.get(qualifiedName);
                if (profileMethod != null && profileMethod.capture()) {
                    return true;
                }
            }
        }
        return Optional.ofNullable(methods.get(qualifiedName))
                       .map(MethodSimulationConfig::capture)
                       .orElse(false);
    }

    @Override
    public Optional<ExhaustionPolicy> exhaustionPolicy(String qualifiedName) {
        if (activeProfile != null) {
            var profileMethods = profiles.get(activeProfile);
            if (profileMethods != null) {
                var profileMethod = profileMethods.get(qualifiedName);
                if (profileMethod != null) {
                    var result = profileMethod.exhaustionPolicy();
                    if (result.isPresent()) {
                        return result;
                    }
                }
            }
        }
        return Optional.ofNullable(methods.get(qualifiedName))
                       .flatMap(MethodSimulationConfig::exhaustionPolicy);
    }

    @Override
    public Optional<Double> threshold(String qualifiedName) {
        if (activeProfile != null) {
            var profileMethods = profiles.get(activeProfile);
            if (profileMethods != null) {
                var profileMethod = profileMethods.get(qualifiedName);
                if (profileMethod != null) {
                    var result = profileMethod.threshold();
                    if (result.isPresent()) {
                        return result;
                    }
                }
            }
        }
        return Optional.ofNullable(methods.get(qualifiedName))
                       .flatMap(MethodSimulationConfig::threshold);
    }

    public Map<String, String> scorerSpecs() {
        return methods.entrySet().stream()
                .filter(e -> e.getValue().scorer().isPresent())
                .collect(Collectors.toMap(Map.Entry::getKey,
                        e -> e.getValue().scorer().orElseThrow()));
    }

    public Map<String, String> extractorSpecs() {
        return methods.entrySet().stream()
                .filter(e -> e.getValue().keyExtractor().isPresent())
                .collect(Collectors.toMap(Map.Entry::getKey,
                        e -> e.getValue().keyExtractor().orElseThrow()));
    }

    public Set<String> profileNames() {
        return Collections.unmodifiableSet(profiles.keySet());
    }

    public Optional<List<String>> activeProfileCorpusFiles() {
        if (activeProfile == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(profileCorpusFiles.get(activeProfile));
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public Optional<SimulationProfile> resolve(String name) {
        var profileMethods = profiles.get(name);
        if (profileMethods == null) {
            return Optional.empty();
        }

        SimulationConfig composedConfig = new SimulationConfig() {
            @Override
            public Optional<String> strategyFor(String qualifiedName) {
                var pm = profileMethods.get(qualifiedName);
                if (pm != null) {
                    var result = pm.strategy();
                    if (result.isPresent()) {return result;}
                }
                return Optional.ofNullable(methods.get(qualifiedName))
                               .flatMap(MethodSimulationConfig::strategy);
            }

            @Override
            public boolean captureEnabled(String qualifiedName) {
                var pm = profileMethods.get(qualifiedName);
                if (pm != null && pm.capture()) {return true;}
                return Optional.ofNullable(methods.get(qualifiedName))
                               .map(MethodSimulationConfig::capture)
                               .orElse(false);
            }

            @Override
            public Optional<ExhaustionPolicy> exhaustionPolicy(String qualifiedName) {
                var pm = profileMethods.get(qualifiedName);
                if (pm != null) {
                    var result = pm.exhaustionPolicy();
                    if (result.isPresent()) {return result;}
                }
                return Optional.ofNullable(methods.get(qualifiedName))
                               .flatMap(MethodSimulationConfig::exhaustionPolicy);
            }

            @Override
            public Optional<Double> threshold(String qualifiedName) {
                var pm = profileMethods.get(qualifiedName);
                if (pm != null) {
                    var result = pm.threshold();
                    if (result.isPresent()) {return result;}
                }
                return Optional.ofNullable(methods.get(qualifiedName))
                               .flatMap(MethodSimulationConfig::threshold);
            }
        };

        var corpus      = new InMemorySimulationCorpus<>();
        var corpusFiles = profileCorpusFiles.get(name);
        if (corpusFiles != null && !corpusFiles.isEmpty()) {
            var loader = new YamlCorpusLoader();
            loader.loadFromPaths(corpusFiles).forEach(corpus::seed);
        }

        return Optional.of(new SimulationProfile(composedConfig, corpus));
    }

}
