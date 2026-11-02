package io.casehub.plugin.catalog.core;

import io.casehub.plugin.catalog.ConflictException;
import io.casehub.plugin.catalog.PluginCatalog;
import io.casehub.plugin.catalog.PluginCoordinates;
import io.casehub.plugin.catalog.PluginEvent;
import io.casehub.plugin.catalog.PluginPackage;
import io.casehub.plugin.catalog.PluginRepository;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class DefaultPluginCatalog implements PluginCatalog {

    private final ConcurrentHashMap<String, PluginPackage> plugins = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<Consumer<PluginEvent>> listeners = new CopyOnWriteArrayList<>();
    private final ConflictDetector conflictDetector = new ConflictDetector();
    private final CopyOnWriteArrayList<PluginRepository> repositories = new CopyOnWriteArrayList<>();

    @Override
    public void install(PluginPackage pluginPackage) throws ConflictException {
        String name = pluginPackage.coordinates().name();
        PluginPackage existing = plugins.get(name);

        if (existing != null) {
            plugins.put(name, pluginPackage);
            fire(new PluginEvent.Updated(pluginPackage, existing));
        } else {
            conflictDetector.check(plugins.values(), pluginPackage);
            plugins.put(name, pluginPackage);
            fire(new PluginEvent.Added(pluginPackage));
        }
    }

    @Override
    public void uninstall(PluginCoordinates coordinates) {
        PluginPackage removed = plugins.remove(coordinates.name());
        if (removed != null) {
            fire(new PluginEvent.Removed(removed));
        }
    }

    @Override
    public Optional<PluginPackage> resolve(String name) {
        return Optional.ofNullable(plugins.get(name));
    }

    @Override
    public List<PluginPackage> installed() {
        return List.copyOf(plugins.values());
    }

    @Override
    public void addRepository(PluginRepository repository) {
        repositories.add(repository);
    }

    @Override
    public void removeRepository(PluginRepository repository) {
        repositories.remove(repository);
    }

    @Override
    public void watch(Consumer<PluginEvent> listener) {
        listeners.add(listener);
    }

    private void fire(PluginEvent event) {
        for (Consumer<PluginEvent> listener : listeners) {
            listener.accept(event);
        }
    }
}
