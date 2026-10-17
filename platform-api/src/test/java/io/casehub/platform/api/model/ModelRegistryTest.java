package io.casehub.platform.api.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ModelRegistryTest {

    @Test
    void emptyRegistryResolvesNothing() {
        var registry = ModelRegistry.empty();
        assertThat(registry.resolveById("any")).isEmpty();
        assertThat(registry.query(ModelQuery.all())).isEmpty();
        assertThat(registry.all()).isEmpty();
    }
}
