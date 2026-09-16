package io.casehub.platform.api.model;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ModelQueryTest {

    @Test
    void all_matchesEverything() {
        var query = ModelQuery.all();
        assertThat(query.vendor()).isNull();
        assertThat(query.family()).isNull();
        assertThat(query.tier()).isNull();
        assertThat(query.requiredCapabilities()).isEmpty();
        assertThat(query.locality()).isNull();
        assertThat(query.maxCostTier()).isNull();
        assertThat(query.authMethod()).isNull();
        assertThat(query.minContextWindow()).isNull();
        assertThat(query.minMaxOutput()).isNull();
        assertThat(query.preferVendor()).isNull();
    }

    @Test
    void builder_setsAllFields() {
        var query = ModelQuery.builder()
                              .vendor("anthropic")
                              .family("claude")
                              .tier(ModelTier.STANDARD)
                              .requiredCapabilities(Set.of(ModelCapabilities.TEXT, ModelCapabilities.VISION))
                              .locality(ModelLocality.CLOUD)
                              .maxCostTier(CostTier.HIGH)
                              .authMethod("api-key")
                              .minContextWindow(128000)
                              .minMaxOutput(16384)
                              .preferVendor("anthropic")
                              .build();

        assertThat(query.vendor()).isEqualTo("anthropic");
        assertThat(query.family()).isEqualTo("claude");
        assertThat(query.tier()).isEqualTo(ModelTier.STANDARD);
        assertThat(query.requiredCapabilities()).containsExactlyInAnyOrder("text", "vision");
        assertThat(query.locality()).isEqualTo(ModelLocality.CLOUD);
        assertThat(query.maxCostTier()).isEqualTo(CostTier.HIGH);
        assertThat(query.authMethod()).isEqualTo("api-key");
        assertThat(query.minContextWindow()).isEqualTo(128000);
        assertThat(query.minMaxOutput()).isEqualTo(16384);
        assertThat(query.preferVendor()).isEqualTo("anthropic");
    }

    @Test
    void nullCapabilities_defaultsToEmptySet() {
        var query = new ModelQuery("anthropic", null, null, null, null, null, null, null, null, null);
        assertThat(query.requiredCapabilities()).isEmpty();
    }

    @Test
    void catalogChangedEvent_hasChanges() {
        var event = new ModelCatalogChangedEvent("src", Set.of("a"), Set.of(), Set.of());
        assertThat(event.hasChanges()).isTrue();

        var empty = new ModelCatalogChangedEvent("src", Set.of(), Set.of(), Set.of());
        assertThat(empty.hasChanges()).isFalse();
    }

    @Test
    void catalogChangedEvent_defensiveCopy() {
        var mutable = new java.util.HashSet<>(Set.of("a"));
        var event = new ModelCatalogChangedEvent("src", mutable, null, null);
        mutable.add("b");
        assertThat(event.addedIds()).containsExactly("a");
        assertThat(event.removedIds()).isEmpty();
        assertThat(event.updatedIds()).isEmpty();
    }
}
