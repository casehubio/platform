package io.casehub.platform.api.preferences;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PreferenceKeyTest {

    record TestPref(String value) implements SingleValuePreference {
        static final TestPref DEFAULT = new TestPref("default");
    }

    @Test
    void qualifiedName_joins_namespace_and_name_with_dot() {
        PreferenceKey<TestPref> key = new PreferenceKey<>("devtown", "humanApprovalThreshold", TestPref.DEFAULT);
        assertEquals("devtown", key.namespace());
        assertEquals("humanApprovalThreshold", key.name());
        assertEquals("devtown.humanApprovalThreshold", key.qualifiedName());
    }

    @Test
    void qualifiedName_is_value_based_for_equality() {
        PreferenceKey<TestPref> k1 = new PreferenceKey<>("devtown", "humanApprovalThreshold", TestPref.DEFAULT);
        PreferenceKey<TestPref> k2 = new PreferenceKey<>("devtown", "humanApprovalThreshold", TestPref.DEFAULT);
        assertEquals(k1, k2);
        assertEquals(k1.hashCode(), k2.hashCode());
    }

    @Test
    void null_namespace_throws() {
        assertThrows(NullPointerException.class, () -> new PreferenceKey<>(null, "key", TestPref.DEFAULT));
    }

    @Test
    void null_name_throws() {
        assertThrows(NullPointerException.class, () -> new PreferenceKey<>("ns", null, TestPref.DEFAULT));
    }

    @Test
    void null_defaultValue_throws() {
        assertThrows(NullPointerException.class, () -> new PreferenceKey<>("ns", "key", null));
    }

    @Test
    void defaultValue_is_returned_by_getOrDefault_when_get_returns_null() {
        TestPref sentinel = new TestPref("sentinel");
        PreferenceKey<TestPref> key = new PreferenceKey<>("ns", "key", sentinel);
        // Preferences.getOrDefault uses key.defaultValue() when get() returns null
        Preferences empty = new MapPreferences(java.util.Map.of());
        assertSame(sentinel, empty.getOrDefault(key));
    }
}
