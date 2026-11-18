package io.casehub.yaml.step.catalog;

import io.casehub.yaml.plugin.api.Definition;
import io.casehub.yaml.plugin.api.PluginRegistry;
import io.casehub.yaml.plugin.api.Result;
import org.junit.jupiter.api.Test;

import java.util.Map;

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

    @Test
    void availableDomains_returnsDistinctDomains() {
        var registry = new CompositePluginRegistry();
        registry.register(defnWithDomain("log", "observability"));
        registry.register(defnWithDomain("trace", "observability"));
        registry.register(defnWithDomain("invoke", "agent"));
        registry.register(def("process"));

        assertThat(registry.availableDomains()).containsExactlyInAnyOrder("observability", "agent");
    }

    @Test
    void actionsInDomain_returnsFilteredActions() {
        var registry = new CompositePluginRegistry();
        registry.register(defnWithDomain("log", "observability"));
        registry.register(defnWithDomain("trace", "observability"));
        registry.register(defnWithDomain("invoke", "agent"));
        registry.register(def("process"));

        assertThat(registry.actionsInDomain("observability")).containsExactlyInAnyOrder("log", "trace");
        assertThat(registry.actionsInDomain("agent")).containsExactlyInAnyOrder("invoke");
    }

    @Test
    void actionsInDomain_nullDomain_returnsUngrouped() {
        var registry = new CompositePluginRegistry();
        registry.register(defnWithDomain("log", "observability"));
        registry.register(def("process"));

        assertThat(registry.actionsInDomain(null)).containsExactlyInAnyOrder("process");
    }

    private static Definition defnWithDomain(String name, String domain) {
        return Definition.of(name)
                         .execute((p, s) -> Result.of(Map.of()))
                         .domain(domain)
                         .build();
    }
}
