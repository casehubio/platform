package io.casehub.yaml.core.runtime;

@FunctionalInterface
public interface RuntimeForEach {
    java.util.List<?> resolve();
}
