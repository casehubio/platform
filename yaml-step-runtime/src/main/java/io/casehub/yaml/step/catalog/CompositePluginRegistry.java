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
        Definition existing = definitions.putIfAbsent(definition.name(), definition);
        if (existing != null) {
            LOG.log(Level.WARNING, "Duplicate plugin registration ignored: ''{0}''", definition.name());
        }
    }

    @Override
    public Optional<Definition> resolve(String actionName) {
        return Optional.ofNullable(definitions.get(actionName));
    }

    @Override
    public Set<String> availableActions() {
        return Set.copyOf(definitions.keySet());
    }
}
