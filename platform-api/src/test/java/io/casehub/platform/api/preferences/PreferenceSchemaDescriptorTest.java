package io.casehub.platform.api.preferences;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class PreferenceSchemaDescriptorTest {

    static final PreferenceKey<IntPreference> INT_KEY = new PreferenceKey<>(
            "casehub.work", "sla.default-hours", IntPreference.of(24), IntPreference::parse);

    static final PreferenceKey<DoublePreference> DOUBLE_KEY = new PreferenceKey<>(
            "casehub.work", "threshold", DoublePreference.of(0.5), DoublePreference::parse);

    static final PreferenceKey<BooleanPreference> BOOL_KEY = new PreferenceKey<>(
            "casehub.platform", "debug.enabled", BooleanPreference.of(false), BooleanPreference::parse);

    static final PreferenceKey<DurationPreference> DURATION_KEY = new PreferenceKey<>(
            "casehub.platform", "session.timeout",
            new DurationPreference(Duration.ofMinutes(30)),
            s -> new DurationPreference(Duration.parse(s)));

    record StringPref(String value) implements SingleValuePreference {
        @Override public String toSerializedValue() { return value; }
    }
    static final PreferenceKey<StringPref> STRING_KEY = new PreferenceKey<>(
            "casehub.platform", "label", new StringPref("default"), StringPref::new);

    record MultiPref(String value) implements MultiValuePreference {
        @Override public String toSerializedValue() { return value; }
    }
    static final PreferenceKey<MultiPref> MULTI_KEY = new PreferenceKey<>(
            "casehub.work", "sla.duration", new MultiPref("PT1H"), MultiPref::new);

    @Test
    void infers_integer_from_IntPreference() {
        PreferenceSchemaDescriptor d = PreferenceSchemaDescriptor.of(INT_KEY).build();
        assertEquals("integer", d.type());
    }

    @Test
    void infers_number_from_DoublePreference() {
        PreferenceSchemaDescriptor d = PreferenceSchemaDescriptor.of(DOUBLE_KEY).build();
        assertEquals("number", d.type());
    }

    @Test
    void infers_boolean_from_BooleanPreference() {
        PreferenceSchemaDescriptor d = PreferenceSchemaDescriptor.of(BOOL_KEY).build();
        assertEquals("boolean", d.type());
    }

    @Test
    void infers_duration_from_DurationPreference() {
        PreferenceSchemaDescriptor d = PreferenceSchemaDescriptor.of(DURATION_KEY).build();
        assertEquals("duration", d.type());
    }

    @Test
    void infers_string_from_unknown_type() {
        PreferenceSchemaDescriptor d = PreferenceSchemaDescriptor.of(STRING_KEY).build();
        assertEquals("string", d.type());
    }

    @Test
    void extracts_namespace_and_name_from_key() {
        PreferenceSchemaDescriptor d = PreferenceSchemaDescriptor.of(INT_KEY).build();
        assertEquals("casehub.work", d.namespace());
        assertEquals("sla.default-hours", d.name());
        assertEquals("casehub.work.sla.default-hours", d.qualifiedName());
    }

    @Test
    void default_value_uses_toSerializedValue() {
        PreferenceSchemaDescriptor d = PreferenceSchemaDescriptor.of(INT_KEY).build();
        assertEquals("24", d.defaultValue());
    }

    @Test
    void duration_default_value_is_iso8601() {
        PreferenceSchemaDescriptor d = PreferenceSchemaDescriptor.of(DURATION_KEY).build();
        assertEquals("PT30M", d.defaultValue());
    }

    @Test
    void label_defaults_to_name() {
        PreferenceSchemaDescriptor d = PreferenceSchemaDescriptor.of(INT_KEY).build();
        assertEquals("sla.default-hours", d.label());
    }

    @Test
    void label_overridden_by_builder() {
        PreferenceSchemaDescriptor d = PreferenceSchemaDescriptor.of(INT_KEY)
                .label("Default SLA hours").build();
        assertEquals("Default SLA hours", d.label());
    }

    @Test
    void description_nullable() {
        PreferenceSchemaDescriptor d = PreferenceSchemaDescriptor.of(INT_KEY).build();
        assertNull(d.description());
    }

    @Test
    void description_set_by_builder() {
        PreferenceSchemaDescriptor d = PreferenceSchemaDescriptor.of(INT_KEY)
                .description("Hours before escalation").build();
        assertEquals("Hours before escalation", d.description());
    }

    @Test
    void constraints_empty_by_default() {
        PreferenceSchemaDescriptor d = PreferenceSchemaDescriptor.of(INT_KEY).build();
        assertTrue(d.constraints().isEmpty());
    }

    @Test
    void constraints_set_by_builder() {
        PreferenceSchemaDescriptor d = PreferenceSchemaDescriptor.of(INT_KEY)
                .constraints(Map.of(
                        PreferenceConstraintKeys.MIN, 1,
                        PreferenceConstraintKeys.MAX, 720))
                .build();
        assertEquals(1, d.constraints().get("min"));
        assertEquals(720, d.constraints().get("max"));
    }

    @Test
    void constraints_are_unmodifiable() {
        PreferenceSchemaDescriptor d = PreferenceSchemaDescriptor.of(INT_KEY)
                .constraints(Map.of("min", 1)).build();
        assertThrows(UnsupportedOperationException.class, () -> d.constraints().put("max", 100));
    }

    @Test
    void options_empty_by_default() {
        PreferenceSchemaDescriptor d = PreferenceSchemaDescriptor.of(INT_KEY).build();
        assertTrue(d.options().isEmpty());
    }

    @Test
    void options_set_by_builder() {
        PreferenceSchemaDescriptor d = PreferenceSchemaDescriptor.of(STRING_KEY)
                .type("enum")
                .options(List.of(
                        new EnumOption("POOL", "Return to pool"),
                        new EnumOption("DELEGATOR", "Return to delegator")))
                .build();
        assertEquals(2, d.options().size());
        assertEquals("POOL", d.options().get(0).value());
        assertEquals("Return to pool", d.options().get(0).label());
    }

    @Test
    void options_are_unmodifiable() {
        PreferenceSchemaDescriptor d = PreferenceSchemaDescriptor.of(STRING_KEY)
                .type("enum")
                .options(List.of(new EnumOption("A", "a")))
                .build();
        assertThrows(UnsupportedOperationException.class,
                () -> d.options().add(new EnumOption("B", "b")));
    }

    @Test
    void type_overridden_by_builder() {
        PreferenceSchemaDescriptor d = PreferenceSchemaDescriptor.of(STRING_KEY)
                .type("enum").build();
        assertEquals("enum", d.type());
    }

    @Test
    void multiValue_false_for_single_value_preference() {
        PreferenceSchemaDescriptor d = PreferenceSchemaDescriptor.of(INT_KEY).build();
        assertFalse(d.multiValue());
    }

    @Test
    void multiValue_true_for_multi_value_preference() {
        PreferenceSchemaDescriptor d = PreferenceSchemaDescriptor.of(MULTI_KEY).build();
        assertTrue(d.multiValue());
    }

    @Test
    void null_namespace_in_record_throws() {
        assertThrows(NullPointerException.class, () -> new PreferenceSchemaDescriptor(
                null, "n", "q", "string", "l", null, "d", false, Map.of(), List.of()));
    }

    @Test
    void null_type_in_record_throws() {
        assertThrows(NullPointerException.class, () -> new PreferenceSchemaDescriptor(
                "ns", "n", "q", null, "l", null, "d", false, Map.of(), List.of()));
    }

    @Test
    void null_defaultValue_in_record_throws() {
        assertThrows(NullPointerException.class, () -> new PreferenceSchemaDescriptor(
                "ns", "n", "q", "string", "l", null, null, false, Map.of(), List.of()));
    }

    @Test
    void null_constraints_defaults_to_empty_map() {
        PreferenceSchemaDescriptor d = new PreferenceSchemaDescriptor(
                "ns", "n", "q", "string", "l", null, "d", false, null, null);
        assertNotNull(d.constraints());
        assertTrue(d.constraints().isEmpty());
    }

    @Test
    void null_options_defaults_to_empty_list() {
        PreferenceSchemaDescriptor d = new PreferenceSchemaDescriptor(
                "ns", "n", "q", "string", "l", null, "d", false, null, null);
        assertNotNull(d.options());
        assertTrue(d.options().isEmpty());
    }

    @Test
    void enum_option_null_value_throws() {
        assertThrows(NullPointerException.class, () -> new EnumOption(null, "label"));
    }

    @Test
    void enum_option_null_label_throws() {
        assertThrows(NullPointerException.class, () -> new EnumOption("value", null));
    }
}
