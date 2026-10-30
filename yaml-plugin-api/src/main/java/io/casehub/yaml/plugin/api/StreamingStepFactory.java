package io.casehub.yaml.plugin.api;

import java.util.Map;

@FunctionalInterface
public interface StreamingStepFactory<I, O> {

    StreamingStep<I, O> create(Map<String, Object> resolvedParams, ServiceRegistry services);
}
