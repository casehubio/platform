package io.casehub.yaml.plugin.api;

import java.util.Optional;
import java.util.Set;

public interface PluginRegistry {

    void register(Definition definition);

    Optional<Definition> unregister(String actionName);

    Optional<Definition> resolve(String actionName);

    Set<String> availableActions();

    default Set<String> availableDomains() {
        Set<String> domains = new java.util.LinkedHashSet<>();
        for (String action : availableActions()) {
            resolve(action).ifPresent(d -> {
                if (d.domain() != null) {domains.add(d.domain());}
            });
        }
        return Set.copyOf(domains);
    }

    default Set<String> actionsInDomain(String domain) {
        Set<String> result = new java.util.LinkedHashSet<>();
        for (String action : availableActions()) {
            resolve(action).ifPresent(d -> {
                if (domain == null ? d.domain() == null : domain.equals(d.domain())) {
                    result.add(action);
                }
            });
        }
        return Set.copyOf(result);
    }
}
