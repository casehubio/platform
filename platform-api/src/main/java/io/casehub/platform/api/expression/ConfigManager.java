package io.casehub.platform.api.expression;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * Provides access to configuration properties, accessible from JQ expressions via
 * {@code $config.{configMapName}.{property}} syntax.
 */
public interface ConfigManager {

    /**
     * Get a single config value.
     *
     * @param propName property name (e.g., "casehub.timeout")
     * @param propClass target type
     * @return value if present
     */
    <T> Optional<T> config(String propName, Class<T> propClass);

    /**
     * Get a multi-valued config (comma-separated).
     *
     * @param propName property name
     * @param propClass element type
     * @return collection of values (empty if not found)
     */
    <T> Collection<T> multiConfig(String propName, Class<T> propClass);

    /**
     * List all known property names.
     */
    Iterable<String> names();

    /**
     * Resolve a config map by name — builds a nested map from all properties with
     * {@code configMapName.} prefix.
     *
     * @param configMapName config map identifier
     * @return map of config properties
     * @throws ConfigMapNotFoundException if no properties with the prefix exist
     */
    Map<String, Object> configMap(String configMapName);
}
