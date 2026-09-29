package io.casehub.platform.expression;

import io.casehub.platform.api.expression.ConfigManager;
import io.casehub.platform.api.expression.ConfigMapNotFoundException;

import java.util.*;

public class ConfigManagerCore implements ConfigManager {

    private final PropertySource source;

    public ConfigManagerCore(PropertySource source) {
        this.source = Objects.requireNonNull(source);
    }

    @Override
    public <T> Optional<T> config(String propName, Class<T> propClass) {
        return source.getProperty(propName).map(v -> convert(v, propClass));
    }

    @Override
    public <T> Collection<T> multiConfig(String propName, Class<T> propClass) {
        return source.getProperty(propName)
                .map(v -> Arrays.stream(v.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .map(s -> convert(s, propClass))
                        .toList())
                .map(list -> (Collection<T>) list)
                .orElse(List.of());
    }

    @Override
    public Iterable<String> names() {
        return source.getPropertyNames();
    }

    @Override
    public Map<String, Object> configMap(String configMapName) {
        String prefix = configMapName + ".";
        Map<String, Object> result = new HashMap<>();
        for (String name : source.getPropertyNames()) {
            if (name.startsWith(prefix)) {
                source.getProperty(name)
                        .ifPresent(v -> PropertyMapBuilder.put(
                                result, name.substring(prefix.length()), v));
            }
        }
        if (result.isEmpty()) throw new ConfigMapNotFoundException(configMapName);
        return result;
    }

    @SuppressWarnings("unchecked")
    static <T> T convert(String value, Class<T> type) {
        if (type == String.class) return (T) value;
        if (type == Integer.class || type == int.class) return (T) Integer.valueOf(value);
        if (type == Long.class || type == long.class) return (T) Long.valueOf(value);
        if (type == Boolean.class || type == boolean.class) return (T) Boolean.valueOf(value);
        if (type == Double.class || type == double.class) return (T) Double.valueOf(value);
        if (type == Float.class || type == float.class) return (T) Float.valueOf(value);
        throw new IllegalArgumentException("Unsupported type: " + type.getName());
    }
}
