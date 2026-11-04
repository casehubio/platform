package io.casehub.yaml.core.annotation;

import java.util.Map;

@FunctionalInterface
public interface InjectionResolver {
    Object resolve(String typeName, Map<String, String> qualifiers);
}
