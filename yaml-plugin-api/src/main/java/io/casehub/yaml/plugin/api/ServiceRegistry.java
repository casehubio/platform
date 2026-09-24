package io.casehub.yaml.plugin.api;

public interface ServiceRegistry {
    <T> T lookup(Class<T> serviceType);
}
