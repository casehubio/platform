package io.casehub.platform.model;

import io.casehub.platform.api.model.ModelDescriptor;
import io.casehub.platform.api.model.ModelQuery;
import io.casehub.platform.api.model.ModelRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class InMemoryModelRegistry implements ModelRegistry {

    private final ConcurrentHashMap<String, ConcurrentHashMap<String, ModelDescriptor>> sources
        = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> sourcePriorities = new ConcurrentHashMap<>();
    private volatile Map<String, ModelDescriptor> resolvedView = Map.of();

    public record CatalogDelta(Set<String> addedIds, Set<String> removedIds, Set<String> updatedIds) {
        public boolean hasChanges() {
            return !addedIds.isEmpty() || !removedIds.isEmpty() || !updatedIds.isEmpty();
        }
    }

    public CatalogDelta replaceSource(String sourceId, int priority, List<ModelDescriptor> models) {
        var oldEntries = sources.getOrDefault(sourceId, new ConcurrentHashMap<>());
        var newEntries = new ConcurrentHashMap<String, ModelDescriptor>();
        for (ModelDescriptor model : models) {
            newEntries.put(model.id(), model);
        }

        Set<String> added = new TreeSet<>();
        Set<String> removed = new TreeSet<>();
        Set<String> updated = new TreeSet<>();

        for (String id : newEntries.keySet()) {
            if (!oldEntries.containsKey(id)) {
                added.add(id);
            } else if (!newEntries.get(id).equals(oldEntries.get(id))) {
                updated.add(id);
            }
        }
        for (String id : oldEntries.keySet()) {
            if (!newEntries.containsKey(id)) {
                removed.add(id);
            }
        }

        if (newEntries.isEmpty()) {
            sources.remove(sourceId);
            sourcePriorities.remove(sourceId);
        } else {
            sources.put(sourceId, newEntries);
            sourcePriorities.put(sourceId, priority);
        }

        rebuildView();
        return new CatalogDelta(added, removed, updated);
    }

    private void rebuildView() {
        var sorted = new ArrayList<>(sourcePriorities.entrySet());
        sorted.sort(Comparator.comparingInt(Map.Entry<String, Integer>::getValue).reversed());

        var view = new LinkedHashMap<String, ModelDescriptor>();
        for (var entry : sorted) {
            var sourceEntries = sources.get(entry.getKey());
            if (sourceEntries != null) {
                for (var modelEntry : sourceEntries.entrySet()) {
                    view.putIfAbsent(modelEntry.getKey(), modelEntry.getValue());
                }
            }
        }
        resolvedView = Map.copyOf(view);
    }

    @Override
    public Optional<ModelDescriptor> resolveById(String modelId) {
        return Optional.ofNullable(resolvedView.get(modelId));
    }

    @Override
    public List<ModelDescriptor> query(ModelQuery query) {
        return resolvedView.values().stream()
            .filter(d -> query.vendor() == null || d.vendor().equals(query.vendor()))
            .filter(d -> query.family() == null || d.family().equals(query.family()))
            .filter(d -> query.tier() == null || d.tier() == query.tier())
            .filter(d -> query.requiredCapabilities().isEmpty()
                || d.capabilities().containsAll(query.requiredCapabilities()))
            .filter(d -> query.locality() == null || d.locality() == query.locality())
            .filter(d -> query.maxCostTier() == null
                || (d.costTier() != null && d.costTier().rank() <= query.maxCostTier().rank()))
            .filter(d -> query.authMethod() == null
                || (d.authMethod() != null && d.authMethod().equals(query.authMethod())))
            .toList();
    }

    @Override
    public List<ModelDescriptor> all() {
        return List.copyOf(resolvedView.values());
    }
}
