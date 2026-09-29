package io.casehub.yaml.plugin.api;

import java.util.Map;

@FunctionalInterface
public interface Action {

    Result execute(Map<String, Object> parameters, ServiceRegistry services);

    default String name() {
        return getClass().getSimpleName();
    }
}
