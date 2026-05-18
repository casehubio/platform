package io.casehub.platform.api.preferences;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class MapPreferences implements Preferences {

    private final Map<String, Object> values;

    public MapPreferences(Map<String, Object> values) {
        Objects.requireNonNull(values, "values must not be null");
        this.values = Collections.unmodifiableMap(new HashMap<>(values));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends SingleValuePreference> T get(PreferenceKey<T> key) {
        Object value = values.get(key.qualifiedName());
        return (value instanceof SingleValuePreference) ? (T) value : null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends MultiValuePreference> T get(PreferenceKey<T> key, String subKey) {
        Object value = values.get(key.qualifiedName() + "." + subKey);
        return (value instanceof MultiValuePreference) ? (T) value : null;
    }

    @Override
    public Map<String, Object> asMap() {
        return values;
    }
}
