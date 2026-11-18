package io.casehub.yaml.step.catalog;

import io.casehub.yaml.plugin.api.Definition;
import io.casehub.yaml.plugin.api.PluginRegistry;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CompositePluginRegistry implements PluginRegistry {

    private static final Logger LOG = Logger.getLogger(CompositePluginRegistry.class.getName());

    private final ConcurrentHashMap<String, Definition> definitions = new ConcurrentHashMap<>();

    @Override
    public void register(Definition definition) {
        Definition existing = definitions.put(definition.name(), definition);
        if (existing != null) {
            LOG.log(Level.WARNING, "Plugin registration collision: ''{0}'' — previous registration overridden", definition.name());
        }
    }

    @Override
    public Optional<Definition> unregister(String actionName) {
        Definition removed = definitions.remove(actionName);
        if (removed != null) {
            LOG.log(Level.INFO, "Plugin unregistered: ''{0}''", actionName);
        }
        return Optional.ofNullable(removed);
    }


    @Override
    public Optional<Definition> resolve(String actionName) {
        return Optional.ofNullable(definitions.get(actionName));
    }

    @Override
    public Set<String> availableActions() {
        return Set.copyOf(definitions.keySet());
    }

    @Override
    public Set<String> availableDomains() {
        Set<String> domains = new java.util.LinkedHashSet<>();
        for (Definition d : definitions.values()) {
            if (d.domain() != null) {domains.add(d.domain());}
        }
        return Set.copyOf(domains);
    }

    @Override
    public Set<String> actionsInDomain(String domain) {
        Set<String> result = new java.util.LinkedHashSet<>();
        for (var e : definitions.entrySet()) {
            String d = e.getValue().domain();
            if (domain == null ? d == null : domain.equals(d)) {
                result.add(e.getKey());
            }
        }
        return Set.copyOf(result);
    }
}
