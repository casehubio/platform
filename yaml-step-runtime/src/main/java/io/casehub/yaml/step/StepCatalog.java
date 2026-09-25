package io.casehub.yaml.step;

import java.util.Optional;
import java.util.Set;

public interface StepCatalog {

    Optional<CatalogEntry> resolve(String actionName);

    Set<String> availableActions();
}
