package io.casehub.platform.preferences.editor;

import io.casehub.platform.api.preferences.BooleanPreference;
import io.casehub.platform.api.preferences.EnumOption;
import io.casehub.platform.api.preferences.IntPreference;
import io.casehub.platform.api.preferences.PreferenceKey;
import io.casehub.platform.api.preferences.PreferenceSchemaDescriptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryPreferenceSchemaRegistryTest {

    InMemoryPreferenceSchemaRegistry registry;

    static final PreferenceKey<IntPreference> KEY_A = new PreferenceKey<>(
            "test", "count", IntPreference.of(0), IntPreference::parse);
    static final PreferenceKey<BooleanPreference> KEY_B = new PreferenceKey<>(
            "test", "flag", BooleanPreference.of(false), BooleanPreference::parse);

    @BeforeEach
    void setUp() {
        registry = new InMemoryPreferenceSchemaRegistry();
    }

    @Test
    void register_and_resolve() {
        PreferenceSchemaDescriptor d = PreferenceSchemaDescriptor.of(KEY_A).label("Count").build();
        registry.register(d);
        assertTrue(registry.resolve("test.count").isPresent());
        assertEquals("Count", registry.resolve("test.count").get().label());
    }

    @Test
    void resolve_unknown_returns_empty() {
        assertTrue(registry.resolve("nonexistent").isEmpty());
    }

    @Test
    void discover_returns_all_registered() {
        registry.register(PreferenceSchemaDescriptor.of(KEY_A).build());
        registry.register(PreferenceSchemaDescriptor.of(KEY_B).build());
        assertEquals(2, registry.discover().size());
    }

    @Test
    void discover_empty_by_default() {
        assertTrue(registry.discover().isEmpty());
    }

    @Test
    void duplicate_registration_overwrites() {
        registry.register(PreferenceSchemaDescriptor.of(KEY_A).label("First").build());
        registry.register(PreferenceSchemaDescriptor.of(KEY_A).label("Second").build());
        assertEquals(1, registry.discover().size());
        assertEquals("Second", registry.resolve("test.count").get().label());
    }
}
