package io.casehub.plugin.catalog;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public interface PluginRepository {

    List<PluginPackage> discover();

    Optional<PluginPackage> fetch(PluginCoordinates coordinates);

    void watch(Consumer<PluginEvent> listener);

    void stopWatching();
}
