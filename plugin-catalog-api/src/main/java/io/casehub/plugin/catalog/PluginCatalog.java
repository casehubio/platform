package io.casehub.plugin.catalog;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public interface PluginCatalog {

    void install(PluginPackage pluginPackage) throws ConflictException;

    void uninstall(PluginCoordinates coordinates);

    Optional<PluginPackage> resolve(String name);

    List<PluginPackage> installed();

    void addRepository(PluginRepository repository);

    void removeRepository(PluginRepository repository);

    void watch(Consumer<PluginEvent> listener);
}
