package io.casehub.yaml.core.resolver;

@FunctionalInterface
public interface ObjectVariableSource {
    Object resolve(String name);
}
