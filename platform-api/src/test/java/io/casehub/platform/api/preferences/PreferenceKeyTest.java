package io.casehub.platform.api.preferences;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PreferenceKeyTest {

    record TestPref(String value) implements SingleValuePreference {
        static final TestPref DEFAULT = new TestPref("default");
    }

    static PreferenceKey<TestPref> key(String ns, String name) {
        return new PreferenceKey<>(ns, name, TestPref.DEFAULT, TestPref::new);
    }

    @Test
    void qualifiedName_joins_namespace_and_name_with_dot() {
        PreferenceKey<TestPref> k = key("devtown", "humanApprovalThreshold");
        assertEquals("devtown", k.namespace());
        assertEquals("humanApprovalThreshold", k.name());
        assertEquals("devtown.humanApprovalThreshold", k.qualifiedName());
    }

    @Test
    void qualifiedName_is_value_based_for_equality() {
        PreferenceKey<TestPref> k1 = key("devtown", "humanApprovalThreshold");
        PreferenceKey<TestPref> k2 = key("devtown", "humanApprovalThreshold");
        // Note: records with Function fields do NOT provide value equality for the function component.
        // Keys are equal when namespace and name match — equality based on qualifiedName() is the contract.
        assertEquals(k1.qualifiedName(), k2.qualifiedName());
    }

    @Test
    void null_namespace_throws() {
        assertThrows(NullPointerException.class, () -> new PreferenceKey<>(null, "key", TestPref.DEFAULT, TestPref::new));
    }

    @Test
    void null_name_throws() {
        assertThrows(NullPointerException.class, () -> new PreferenceKey<>("ns", null, TestPref.DEFAULT, TestPref::new));
    }

    @Test
    void null_defaultValue_throws() {
        assertThrows(NullPointerException.class, () -> new PreferenceKey<>("ns", "key", null, TestPref::new));
    }

    @Test
    void null_parser_throws() {
        assertThrows(NullPointerException.class, () -> new PreferenceKey<>("ns", "key", TestPref.DEFAULT, null));
    }

    @Test
    void parse_converts_string_to_typed_value() {
        PreferenceKey<TestPref> k = key("ns", "key");
        TestPref result = k.parse("hello");
        assertEquals("hello", result.value());
    }

    @Test
    void defaultValue_is_returned_by_getOrDefault_when_get_returns_null() {
        TestPref sentinel = new TestPref("sentinel");
        PreferenceKey<TestPref> k = new PreferenceKey<>("ns", "key", sentinel, TestPref::new);
        Preferences empty = new MapPreferences(java.util.Map.of());
        assertSame(sentinel, empty.getOrDefault(k));
    }
}
