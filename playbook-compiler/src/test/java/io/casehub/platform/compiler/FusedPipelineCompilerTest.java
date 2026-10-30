package io.casehub.platform.compiler;

import io.casehub.yaml.plugin.api.Definition;
import io.casehub.yaml.plugin.api.PluginRegistry;
import io.casehub.yaml.plugin.api.Portability;
import io.casehub.yaml.plugin.api.Result;
import io.casehub.yaml.plugin.api.StreamingTypeInfo;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class FusedPipelineCompilerTest {

    @Test
    void compilerAcceptsOptionalPluginRegistry() {
        var registry = new TestPluginRegistry();
        var compiler = new PlaybookCompiler(registry);
        assertTrue(compiler.pluginRegistry().isPresent());
    }

    @Test
    void compilerWithoutRegistryHasEmptyOptional() {
        var compiler = new PlaybookCompiler();
        assertTrue(compiler.pluginRegistry().isEmpty());
    }

    @Test
    void fusedPipelineCompilerProducesTaskItem() {
        var compiler = new FusedPipelineCompiler();
        var ctx = createTestContext();
        var item = compiler.compile("fused", Map.of(), Map.of(), ctx);
        assertNotNull(item);
        assertNotNull(item.getTask());
    }

    private CompilationContext createTestContext() {
        return new CompilationContext("/test", 0,
            new SourceMap.Builder(), new CoordinationSpec.Builder(),
            new java.util.ArrayList<>(), new PlaybookCompiler());
    }

    private static class TestPluginRegistry implements PluginRegistry {
        private final Map<String, Definition> defs = new HashMap<>();

        @Override public void register(Definition definition) {
            defs.put(definition.name(), definition);
        }

        @Override public Optional<Definition> resolve(String actionName) {
            return Optional.ofNullable(defs.get(actionName));
        }

        @Override public Set<String> availableActions() {
            return defs.keySet();
        }
    }
}
