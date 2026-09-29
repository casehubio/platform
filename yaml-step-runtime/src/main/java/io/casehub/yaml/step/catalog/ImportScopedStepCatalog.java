package io.casehub.yaml.step.catalog;

import io.casehub.yaml.plugin.api.Definition;
import io.casehub.yaml.plugin.api.PluginRegistry;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class ImportScopedStepCatalog implements PluginRegistry {

    private final Map<String, Definition> importedEntries;
    private final PluginRegistry delegate;

    public ImportScopedStepCatalog(Map<String, Definition> importedEntries,
                                    PluginRegistry delegate) {
        this.importedEntries = importedEntries;
        this.delegate = delegate;
    }

    @Override
    public void register(Definition definition) {
        delegate.register(definition);
    }

    @Override
    public Optional<Definition> resolve(String actionName) {
        Definition imported = importedEntries.get(actionName);
        if (imported != null) return Optional.of(imported);
        return delegate.resolve(actionName);
    }

    @Override
    public Set<String> availableActions() {
        Set<String> all = new LinkedHashSet<>(importedEntries.keySet());
        all.addAll(delegate.availableActions());
        return Set.copyOf(all);
    }
}
