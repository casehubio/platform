package io.casehub.template.registry;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LanguageFamilyRegistryTest {

    @Test
    void emptyRegistryReturnsEmpty() {
        var registry = new LanguageFamilyRegistry();
        assertThat(registry.familyFor("en")).isEmpty();
    }

    @Test
    void registerAndRetrieve() {
        var registry = new LanguageFamilyRegistry();
        registry.register("en", "svo");
        registry.register("fr", "svo");
        assertThat(registry.familyFor("en")).hasValue("svo");
        assertThat(registry.familyFor("fr")).hasValue("svo");
        assertThat(registry.familyFor("de")).isEmpty();
    }

    @Test
    void registerOverridesPrevious() {
        var registry = new LanguageFamilyRegistry();
        registry.register("de", "svo");
        registry.register("de", "germanic");
        assertThat(registry.familyFor("de")).hasValue("germanic");
    }

    @Test
    void registeredLanguagesReturnsAll() {
        var registry = new LanguageFamilyRegistry();
        registry.register("en", "svo");
        registry.register("fr", "svo");
        registry.register("ja", "cjk");
        assertThat(registry.registeredLanguages()).containsExactlyInAnyOrder("en", "fr", "ja");
    }
}
