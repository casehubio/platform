package io.casehub.plugin.catalog;

public sealed interface PluginEvent {

    PluginPackage pluginPackage();

    record Added(PluginPackage pluginPackage) implements PluginEvent {}

    record Removed(PluginPackage pluginPackage) implements PluginEvent {}

    record Updated(PluginPackage pluginPackage, PluginPackage previous) implements PluginEvent {
        @Override
        public PluginPackage pluginPackage() {
            return pluginPackage;
        }
    }
}
