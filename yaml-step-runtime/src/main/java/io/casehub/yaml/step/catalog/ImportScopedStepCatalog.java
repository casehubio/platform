package io.casehub.yaml.step.catalog;

import io.casehub.yaml.step.CatalogEntry;
import io.casehub.yaml.step.StepCatalog;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class ImportScopedStepCatalog implements StepCatalog {

    private final Map<String, CatalogEntry> importedEntries;
    private final StepCatalog delegate;

    public ImportScopedStepCatalog(Map<String, CatalogEntry> importedEntries,
                                    StepCatalog delegate) {
        this.importedEntries = importedEntries;
        this.delegate = delegate;
    }

    @Override
    public Optional<CatalogEntry> resolve(String actionName) {
        CatalogEntry imported = importedEntries.get(actionName);
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
