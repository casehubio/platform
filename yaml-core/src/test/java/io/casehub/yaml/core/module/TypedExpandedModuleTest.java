package io.casehub.yaml.core.module;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class TypedExpandedModuleTest {

    @Test
    void content_accessor_returns_typed_object() {
        var module = new TypedExpandedModule<>("typed-content",
                Map.of(), Map.of(), Map.of());
        assertThat(module.content()).isEqualTo("typed-content");
    }

    @Test
    void output_source_resolves_alias_dot_name() {
        var module = new TypedExpandedModule<>("content",
                Map.of(), Map.of(),
                Map.of("monitoring", Map.of("endpoint", "https://example.com")));
        assertThat(module.outputSource().resolve("monitoring.endpoint"))
                .isEqualTo("https://example.com");
    }

    @Test
    void output_source_unknown_alias_returns_null() {
        var module = new TypedExpandedModule<>("content",
                Map.of(), Map.of(), Map.of());
        assertThat(module.outputSource().resolve("unknown.key")).isNull();
    }

    @Test
    void output_source_no_dot_returns_null() {
        var module = new TypedExpandedModule<>("content",
                Map.of(), Map.of(),
                Map.of("monitoring", Map.of("endpoint", "val")));
        assertThat(module.outputSource().resolve("monitoring")).isNull();
    }
}
