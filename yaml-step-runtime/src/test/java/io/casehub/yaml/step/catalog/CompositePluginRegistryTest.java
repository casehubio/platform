package io.casehub.yaml.step.catalog;

import io.casehub.yaml.plugin.api.Definition;
import io.casehub.yaml.plugin.api.PluginRegistry;
import io.casehub.yaml.plugin.api.Result;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CompositePluginRegistryTest {

    private final PluginRegistry registry = new CompositePluginRegistry();

    private static Definition def(String name) {
        return Definition.of(name)
                .description(name + " action")
                .execute((params, services) -> Result.of(java.util.Map.of()))
                .build();
    }

    @Test
    void register_replacesExisting() {
        Definition first = def("greet");
        Definition second = Definition.of("greet")
                .description("replaced action")
                .execute((params, services) -> Result.of(java.util.Map.of()))
                .build();

        registry.register(first);
        registry.register(second);

        assertThat(registry.resolve("greet"))
                .isPresent()
                .hasValueSatisfying(d ->
                        assertThat(d.description()).isEqualTo("replaced action"));
    }

    @Test
    void unregister_removesDefinition() {
        registry.register(def("greet"));
        assertThat(registry.resolve("greet")).isPresent();

        var removed = registry.unregister("greet");

        assertThat(removed).isPresent();
        assertThat(registry.resolve("greet")).isEmpty();
        assertThat(registry.availableActions()).doesNotContain("greet");
    }

    @Test
    void unregister_returnsEmptyForUnknown() {
        assertThat(registry.unregister("nonexistent")).isEmpty();
    }

    @Test
    void register_afterUnregister_works() {
        registry.register(def("greet"));
        registry.unregister("greet");

        registry.register(def("greet"));
        assertThat(registry.resolve("greet")).isPresent();
    }
}
