package io.casehub.yaml.plugin.api;

import java.util.Optional;
import java.util.Set;

public interface PluginRegistry {

    void register(Definition definition);

    Optional<Definition> resolve(String actionName);

    Set<String> availableActions();
}
