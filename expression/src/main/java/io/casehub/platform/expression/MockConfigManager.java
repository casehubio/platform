package io.casehub.platform.expression;

import io.casehub.platform.api.expression.ConfigMapNotFoundException;
import io.casehub.platform.api.expression.ConfigManager;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * {@code @DefaultBean} mock for {@link ConfigManager}.
 *
 * <p>Delegates to SmallRye Config (MicroProfile Config API) — application.properties,
 * env vars, and any registered ConfigSources. This is the appropriate default for
 * Quarkus deployments; no separate implementation is needed unless a non-Quarkus
 * config backend is required.
 *
 * <p>Displaced automatically when a non-default {@code @ApplicationScoped} {@link ConfigManager}
 * is on the classpath.
 */
@DefaultBean
@ApplicationScoped
public class MockConfigManager implements ConfigManager {

    private Config config() {
        return ConfigProvider.getConfig();
    }

    @Override
    public <T> Optional<T> config(String propName, Class<T> propClass) {
        return config().getOptionalValue(propName, propClass);
    }

    @Override
    public <T> Collection<T> multiConfig(String propName, Class<T> propClass) {
        try {
            List<T> result = new ArrayList<>();
            config().getOptionalValues(propName, propClass).ifPresent(result::addAll);
            return result;
        } catch (Exception e) {
            return List.of();
        }
    }

    @Override
    public Iterable<String> names() {
        return config().getPropertyNames();
    }

    @Override
    public Map<String, Object> configMap(String configMapName) {
        String prefix = configMapName + ".";
        Map<String, Object> result = new HashMap<>();
        for (String name : config().getPropertyNames()) {
            if (name.startsWith(prefix)) {
                config().getOptionalValue(name, String.class)
                        .ifPresent(v -> put(result, name.substring(prefix.length()), v));
            }
        }
        if (result.isEmpty()) throw new ConfigMapNotFoundException(configMapName);
        return result;
    }

    @SuppressWarnings("unchecked")
    private static void put(Map<String, Object> map, String key, String value) {
        int dot = key.indexOf('.');
        if (dot == -1) { map.put(key, value); return; }
        String head = key.substring(0, dot);
        String tail = key.substring(dot + 1);
        Map<String, Object> nested = (Map<String, Object>)
                map.computeIfAbsent(head, k -> new HashMap<>());
        put(nested, tail, value);
    }
}
