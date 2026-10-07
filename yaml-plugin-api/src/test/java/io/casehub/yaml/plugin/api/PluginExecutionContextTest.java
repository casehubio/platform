package io.casehub.yaml.plugin.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PluginExecutionContextTest {

    @Test
    void executionId_returns_provided_value() {
        PluginExecutionContext ctx = () -> "exec-123";
        assertThat(ctx.executionId()).isEqualTo("exec-123");
    }

    @Test
    void lookup_via_service_registry_returns_context() {
        PluginExecutionContext ctx = () -> "exec-456";
        var registry = new MapServiceRegistry();
        registry.register(PluginExecutionContext.class, ctx);
        PluginExecutionContext resolved = registry.lookup(PluginExecutionContext.class);
        assertThat(resolved).isNotNull();
        assertThat(resolved.executionId()).isEqualTo("exec-456");
    }

    @Test
    void lookup_returns_null_when_not_populated() {
        var registry = new MapServiceRegistry();
        PluginExecutionContext resolved = registry.lookup(PluginExecutionContext.class);
        assertThat(resolved).isNull();
    }
}
