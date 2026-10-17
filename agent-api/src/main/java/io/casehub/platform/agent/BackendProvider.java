package io.casehub.platform.agent;

import java.util.Map;

/** ServiceLoader-discovered factory for creating AgentBackend instances from manifest properties. */
public interface BackendProvider {
    String vendorKey();
    AgentBackend create(Map<String, String> properties);

    default int intProperty(Map<String, String> properties, String key, int defaultValue) {
        var value = properties.get(key);
        if (value == null) {return defaultValue;}
        try {return Integer.parseInt(value);} catch (NumberFormatException e) {return defaultValue;}
    }

    default long longProperty(Map<String, String> properties, String key, long defaultValue) {
        var value = properties.get(key);
        if (value == null) {return defaultValue;}
        try {return Long.parseLong(value);} catch (NumberFormatException e) {return defaultValue;}
    }

    default java.time.Duration durationProperty(Map<String, String> properties, String key, long defaultSeconds) {
        return java.time.Duration.ofSeconds(longProperty(properties, key, defaultSeconds));
    }

}
