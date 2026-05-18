package io.casehub.platform.api.preferences;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PreferenceKeyTest {

    @Test
    void qualifiedName_joins_namespace_and_name_with_dot() {
        PreferenceKey<SingleValuePreference> key = new PreferenceKey<>("devtown", "humanApprovalThreshold");
        assertEquals("devtown", key.namespace());
        assertEquals("humanApprovalThreshold", key.name());
        assertEquals("devtown.humanApprovalThreshold", key.qualifiedName());
    }

    @Test
    void qualifiedName_is_value_based_for_equality() {
        PreferenceKey<SingleValuePreference> k1 = new PreferenceKey<>("devtown", "humanApprovalThreshold");
        PreferenceKey<SingleValuePreference> k2 = new PreferenceKey<>("devtown", "humanApprovalThreshold");
        assertEquals(k1, k2);
        assertEquals(k1.hashCode(), k2.hashCode());
    }

    @Test
    void null_namespace_throws() {
        assertThrows(NullPointerException.class, () -> new PreferenceKey<>(null, "key"));
    }

    @Test
    void null_name_throws() {
        assertThrows(NullPointerException.class, () -> new PreferenceKey<>("ns", null));
    }
}
