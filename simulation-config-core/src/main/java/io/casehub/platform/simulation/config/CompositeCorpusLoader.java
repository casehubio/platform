package io.casehub.platform.simulation.config;

import io.casehub.platform.simulation.InvocationRecord;
import io.casehub.platform.simulation.SimulationConfigException;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CompositeCorpusLoader {

    private final List<CorpusLoader> loaders;

    public CompositeCorpusLoader(CorpusLoader... loaders) {
        this.loaders = List.of(loaders);
    }

    public boolean supports(String path) {
        return loaders.stream().anyMatch(l -> l.supports(path));
    }

    public Map<String, List<InvocationRecord<Object, Object>>> loadFromPaths(
            List<String> paths, String defaultTenancyId) {
        Map<String, List<InvocationRecord<Object, Object>>> merged = new HashMap<>();
        for (String path : paths) {
            String trimmed = path.trim();
            CorpusLoader loader = loaders.stream()
                    .filter(l -> l.supports(trimmed))
                    .findFirst()
                    .orElseThrow(() -> new SimulationConfigException(
                            "No corpus loader for: " + trimmed
                            + ". Supported extensions: .yaml, .yml, .json, .csv"));
            try (InputStream is = StreamResolver.openStream(trimmed)) {
                loader.load(is, defaultTenancyId).forEach((qn, records) ->
                        merged.computeIfAbsent(qn, k -> new ArrayList<>()).addAll(records));
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to load corpus file: " + trimmed, e);
            }
        }
        return merged;
    }
}
