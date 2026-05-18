package io.casehub.platform.api.preferences;

import java.util.Map;

public interface Preferences {

    /**
     * Returns the preference for the given key, or {@code null} if not set.
     * Callers must fall back to the Preference record's {@code DEFAULT} constant:
     * <pre>
     *   HumanApprovalThreshold t = prefs.get(HumanApprovalThreshold.KEY);
     *   int value = t != null ? t.value() : HumanApprovalThreshold.DEFAULT.value();
     * </pre>
     */
    <T extends SingleValuePreference> T get(PreferenceKey<T> key);

    /**
     * Returns the multi-value preference for the given key and sub-key, or {@code null} if not set.
     */
    <T extends MultiValuePreference> T get(PreferenceKey<T> key, String subKey);

    /** Returns all values as a flat map, suitable for CaseContext/JQ injection. */
    Map<String, Object> asMap();
}
