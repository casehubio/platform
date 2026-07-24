package io.casehub.platform.api.preferences;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BooleanPreferenceTest {

    @Test
    void of_true() {
        assertTrue(BooleanPreference.of(true).value());
    }

    @Test
    void of_false() {
        assertFalse(BooleanPreference.of(false).value());
    }

    @Test
    void parse_true_lowercase() {
        assertTrue(BooleanPreference.parse("true").value());
    }

    @Test
    void parse_false_lowercase() {
        assertFalse(BooleanPreference.parse("false").value());
    }

    @Test
    void parse_true_uppercase() {
        assertTrue(BooleanPreference.parse("TRUE").value());
    }

    @Test
    void parse_true_mixed_case() {
        assertTrue(BooleanPreference.parse("True").value());
    }

    @Test
    void parse_invalid_throws() {
        assertThrows(IllegalArgumentException.class, () -> BooleanPreference.parse("yes"));
    }

    @Test
    void parse_arbitrary_string_throws() {
        assertThrows(IllegalArgumentException.class, () -> BooleanPreference.parse("1"));
    }

    @Test
    void parse_typo_throws() {
        assertThrows(IllegalArgumentException.class, () -> BooleanPreference.parse("treu"));
    }

    @Test
    void parse_null_throws() {
        assertThrows(NullPointerException.class, () -> BooleanPreference.parse(null));
    }

    @Test
    void toSerializedValue_true() {
        assertEquals("true", BooleanPreference.of(true).toSerializedValue());
    }

    @Test
    void toSerializedValue_false() {
        assertEquals("false", BooleanPreference.of(false).toSerializedValue());
    }

    @Test
    void implements_single_value_preference() {
        assertInstanceOf(SingleValuePreference.class, BooleanPreference.of(true));
    }
}
