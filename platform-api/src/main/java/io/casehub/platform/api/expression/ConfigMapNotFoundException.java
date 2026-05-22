package io.casehub.platform.api.expression;

/**
 * Thrown when a requested config map does not exist (no properties with the given prefix).
 */
public class ConfigMapNotFoundException extends RuntimeException {

    private final String configMapName;

    public ConfigMapNotFoundException(String configMapName) {
        super("ConfigMap not found: " + configMapName);
        this.configMapName = configMapName;
    }

    public String getConfigMapName() {
        return configMapName;
    }
}
