package io.casehub.yaml.plugin.api;

import java.util.Map;

@FunctionalInterface
public interface StepAction {

    StepResult execute(Map<String, Object> parameters, ServiceRegistry services);

    default String name() {
        return getClass().getSimpleName();
    }
}
