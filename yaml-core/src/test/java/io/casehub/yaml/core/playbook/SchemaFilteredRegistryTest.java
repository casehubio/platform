package io.casehub.yaml.core.playbook;

import io.casehub.yaml.plugin.api.Definition;
import io.casehub.yaml.plugin.api.PluginRegistry;
import io.casehub.yaml.plugin.api.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

class SchemaFilteredRegistryTest {

    private static final io.casehub.yaml.plugin.api.Action NOOP =
            (p, s) -> Result.of(Map.of());

    private MapPlaybookSchemaRegistry schemas;
    private SimplePluginRegistry delegate;
    private SchemaFilteredRegistry filtered;

    @BeforeEach
    void setUp() {
        schemas = new MapPlaybookSchemaRegistry();
        schemas.register(PlaybookSchemaDescriptor.domain(
                "clinical-server", "server", Set.of("clinical-trial")));

        delegate = new SimplePluginRegistry();
        delegate.register(Definition.of("rest-call")
                .capability("steps").execute(NOOP).build());
        delegate.register(Definition.of("correlate")
                .capability("correlation").execute(NOOP).build());
        delegate.register(Definition.of("clinical-enroll")
                .capability("clinical-trial").execute(NOOP).build());
        delegate.register(Definition.of("spotlight")
                .capability("spotlight").execute(NOOP).build());

        filtered = SchemaFilteredRegistry.forSchema(
                delegate, schemas, "clinical-server");
    }

    @Test
    void resolve_allowsMatchingCapability() {
        assertThat(filtered.resolve("rest-call")).isPresent();
        assertThat(filtered.resolve("correlate")).isPresent();
        assertThat(filtered.resolve("clinical-enroll")).isPresent();
    }

    @Test
    void resolve_filtersNonMatchingCapability() {
        assertThat(filtered.resolve("spotlight")).isEmpty();
    }

    @Test
    void availableActions_onlyListsMatchingPlugins() {
        assertThat(filtered.availableActions())
                .contains("rest-call", "correlate", "clinical-enroll")
                .doesNotContain("spotlight");
    }

    @Test
    void register_delegatesToUnderlying() {
        filtered.register(Definition.of("new-step")
                .capability("steps").execute(NOOP).build());
        assertThat(delegate.resolve("new-step")).isPresent();
    }

    @Test
    void forSchema_bridgesSchemaAndPluginRegistries() {
        var clientFiltered = SchemaFilteredRegistry.forSchema(
                delegate, schemas, "client");
        assertThat(clientFiltered.resolve("spotlight")).isPresent();
        assertThat(clientFiltered.resolve("correlate")).isEmpty();
    }

    @Test
    void assisted_excludesRiskCapabilities() {
        delegate.register(Definition.of("process")
                                    .capability("process-execution").execute(NOOP).build());
        delegate.register(Definition.of("raw-rest")
                                    .capability("raw-http").execute(NOOP).build());
        delegate.register(Definition.of("safe-step")
                                    .capability("steps").execute(NOOP).build());

        var assistedFiltered = SchemaFilteredRegistry.forSchema(
                delegate, schemas, "assisted");

        assertThat(assistedFiltered.resolve("process")).isEmpty();
        assertThat(assistedFiltered.resolve("raw-rest")).isEmpty();
        assertThat(assistedFiltered.resolve("safe-step")).isPresent();
    }

    @Test
    void assisted_excludesMvelAndEnvCapabilities() {
        delegate.register(Definition.of("mvel-eval")
                                    .capability("expression-mvel").execute(NOOP).build());
        delegate.register(Definition.of("env-read")
                                    .capability("env-access").execute(NOOP).build());
        delegate.register(Definition.of("jq-eval")
                                    .capability("steps").execute(NOOP).build());

        var assistedFiltered = SchemaFilteredRegistry.forSchema(
                delegate, schemas, "assisted");

        assertThat(assistedFiltered.resolve("mvel-eval")).isEmpty();
        assertThat(assistedFiltered.resolve("env-read")).isEmpty();
        assertThat(assistedFiltered.resolve("jq-eval")).isPresent();
    }

    @Test
    void server_includesRiskCapabilities() {
        delegate.register(Definition.of("process")
                                    .capability("process-execution").execute(NOOP).build());
        delegate.register(Definition.of("raw-rest")
                                    .capability("raw-http").execute(NOOP).build());

        var serverFiltered = SchemaFilteredRegistry.forSchema(
                delegate, schemas, "server");

        assertThat(serverFiltered.resolve("process")).isPresent();
        assertThat(serverFiltered.resolve("raw-rest")).isPresent();
    }

    static class SimplePluginRegistry implements PluginRegistry {
        private final ConcurrentHashMap<String, Definition> defs = new ConcurrentHashMap<>();
        @Override public void register(Definition d) { defs.put(d.name(), d); }

        @Override
        public Optional<Definition> unregister(String actionName) {
            return Optional.ofNullable(defs.remove(actionName));
        }

        @Override public Optional<Definition> resolve(String name) {
            return Optional.ofNullable(defs.get(name));
        }
        @Override public Set<String> availableActions() { return defs.keySet(); }
    }
}
