package io.casehub.yaml.plugin.api;

import java.util.HashMap;
import java.util.Map;

public class MapServiceRegistry implements ServiceRegistry {

    private final Map<Class<?>, Object> services = new HashMap<>();

    public <T> MapServiceRegistry register(Class<T> type, T instance) {
        services.put(type, instance);
        return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T lookup(Class<T> serviceType) {
        return (T) services.get(serviceType);
    }
}
