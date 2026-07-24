package io.casehub.platform.api.preferences;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;

class PreferenceSerializationTest {

    @Test
    void int_serializes_to_string_value() {
        assertEquals("24", IntPreference.of(24).toSerializedValue());
    }

    @Test
    void int_negative_serializes() {
        assertEquals("-5", IntPreference.of(-5).toSerializedValue());
    }

    @Test
    void int_zero_serializes() {
        assertEquals("0", IntPreference.of(0).toSerializedValue());
    }

    @Test
    void double_serializes_to_string_value() {
        assertEquals("0.5", DoublePreference.of(0.5).toSerializedValue());
    }

    @Test
    void double_integer_value_serializes() {
        assertEquals("1.0", DoublePreference.of(1.0).toSerializedValue());
    }

    @Test
    void boolean_true_serializes() {
        assertEquals("true", BooleanPreference.of(true).toSerializedValue());
    }

    @Test
    void boolean_false_serializes() {
        assertEquals("false", BooleanPreference.of(false).toSerializedValue());
    }

    @Test
    void duration_serializes_to_iso8601() {
        assertEquals("PT30M", new DurationPreference(Duration.ofMinutes(30)).toSerializedValue());
    }

    @Test
    void duration_hours_serializes() {
        assertEquals("PT24H", new DurationPreference(Duration.ofHours(24)).toSerializedValue());
    }

    @Test
    void roundtrip_int() {
        IntPreference original = IntPreference.of(42);
        assertEquals(original, IntPreference.parse(original.toSerializedValue()));
    }

    @Test
    void roundtrip_double() {
        DoublePreference original = DoublePreference.of(3.14);
        assertEquals(original, DoublePreference.parse(original.toSerializedValue()));
    }

    @Test
    void roundtrip_boolean() {
        BooleanPreference original = BooleanPreference.of(true);
        assertEquals(original, BooleanPreference.parse(original.toSerializedValue()));
    }
}
