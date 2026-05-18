package io.casehub.platform.api.preferences;

import java.util.Map;

public interface Preferences {

    /**
     * Returns the preference for the given key, or {@code null} if not set.
     * Use {@link #getOrDefault(PreferenceKey)} to apply the key's compile-time default automatically.
     */
    <T extends SingleValuePreference> T get(PreferenceKey<T> key);

    /**
     * Returns the multi-value preference for the given key and sub-key, or {@code null} if not set.
     */
    <T extends MultiValuePreference> T get(PreferenceKey<T> key, String subKey);

    /**
     * Returns the preference for the given key, falling back to {@code key.defaultValue()} if not set.
     * Never returns null.
     */
    default <T extends SingleValuePreference> T getOrDefault(PreferenceKey<T> key) {
        T value = get(key);
        return value != null ? value : key.defaultValue();
    }

    /**
     * Returns the multi-value preference for the given key and sub-key,
     * falling back to {@code key.defaultValue()} if not set. Never returns null.
     */
    default <T extends MultiValuePreference> T getOrDefault(PreferenceKey<T> key, String subKey) {
        T value = get(key, subKey);
        return value != null ? value : key.defaultValue();
    }

    /** Returns all values as a flat map, suitable for injection into CaseContext. */
    Map<String, Object> asMap();
}
