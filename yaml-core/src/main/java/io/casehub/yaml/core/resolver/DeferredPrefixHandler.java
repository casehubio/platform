package io.casehub.yaml.core.resolver;

@FunctionalInterface
public interface DeferredPrefixHandler {
    void onDeferred(String prefix, String key, String elementContext);
}
