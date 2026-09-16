package io.casehub.platform.agent.config;

@FunctionalInterface
public interface LocalModelReconciler {
    void ensurePresent(String modelId);
}
