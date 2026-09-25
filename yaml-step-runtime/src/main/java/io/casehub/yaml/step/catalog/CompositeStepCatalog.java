package io.casehub.yaml.step.catalog;

import io.casehub.yaml.step.CatalogEntry;
import io.casehub.yaml.step.CatalogSource;
import io.casehub.yaml.step.StepCatalog;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class CompositeStepCatalog implements StepCatalog {

    private final Map<String, CatalogEntry> entries = new ConcurrentHashMap<>();
    private volatile boolean initialized = false;
    private final List<CatalogSource> sources;

    public CompositeStepCatalog(List<CatalogSource> sources) {
        this.sources = sources;
    }

    public void initialize() {
        sources.stream()
               .sorted(java.util.Comparator.comparingInt(CatalogSource::priority))
               .forEach(source -> {
                   Map<String, CatalogEntry> sourceEntries = new java.util.LinkedHashMap<>();
                   source.populate(sourceEntries);
                   sourceEntries.forEach(entries::putIfAbsent);
               });
        initialized = true;
    }

    @Override
    public Optional<CatalogEntry> resolve(String actionName) {
        if (!initialized) {
            throw new IllegalStateException("Step catalog not yet initialized");
        }
        return Optional.ofNullable(entries.get(actionName));
    }

    @Override
    public Set<String> availableActions() {
        return Set.copyOf(entries.keySet());
    }
}
