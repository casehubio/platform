package io.casehub.yaml.test;

import io.casehub.yaml.plugin.api.MapServiceRegistry;
import io.casehub.yaml.plugin.api.Result;
import io.casehub.yaml.step.catalog.CompositePluginRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AgentSessionTestSupportTest {

    @Test
    void setup_registersAllFourPlugins() {
        var pluginRegistry = new CompositePluginRegistry();
        var simulateBlock = Map.<String, Object>of(
                "agent", List.of(
                        Map.of("prompt", "hello", "response", "world")));

        try (var support = AgentSessionTestSupport.setup(simulateBlock, pluginRegistry, null, null)) {
            assertThat(pluginRegistry.resolve("agent-session-open")).isPresent();
            assertThat(pluginRegistry.resolve("agent-session-query")).isPresent();
            assertThat(pluginRegistry.resolve("agent-session-clear")).isPresent();
            assertThat(pluginRegistry.resolve("agent-session-close")).isPresent();
        }
    }

    @Test
    void openQueryClose_returnsSimulatedResponse() {
        var pluginRegistry = new CompositePluginRegistry();
        var simulateBlock = Map.<String, Object>of(
                "agent", List.of(
                        Map.of("prompt", "hello", "response", "world"),
                        Map.of("prompt", "classify", "response", "INVOICE")));

        try (var support = AgentSessionTestSupport.setup(simulateBlock, pluginRegistry, null, null)) {
            var services = new MapServiceRegistry();

            var openDef = pluginRegistry.resolve("agent-session-open").orElseThrow();
            Result openResult = openDef.action().execute(
                    Map.of("session", "test-session", "system", "You are helpful"), services);
            assertThat(openResult.isSuccess()).isTrue();

            var queryDef = pluginRegistry.resolve("agent-session-query").orElseThrow();
            Result queryResult = queryDef.action().execute(
                    Map.of("session", "test-session", "user", "hello there"), services);
            assertThat(queryResult.isSuccess()).isTrue();
            assertThat(queryResult.output().get("response").toString()).contains("world");

            var closeDef = pluginRegistry.resolve("agent-session-close").orElseThrow();
            Result closeResult = closeDef.action().execute(
                    Map.of("session", "test-session"), services);
            assertThat(closeResult.isSuccess()).isTrue();
        }
    }

    @Test
    void clearStep_executesWithoutError() {
        var pluginRegistry = new CompositePluginRegistry();
        var simulateBlock = Map.<String, Object>of(
                "agent", List.of(
                        Map.of("prompt", "test", "response", "response")));

        try (var support = AgentSessionTestSupport.setup(simulateBlock, pluginRegistry, null, null)) {
            var services = new MapServiceRegistry();

            pluginRegistry.resolve("agent-session-open").orElseThrow()
                    .action().execute(Map.of("session", "s1", "system", "test"), services);

            pluginRegistry.resolve("agent-session-query").orElseThrow()
                    .action().execute(Map.of("session", "s1", "user", "test query"), services);

            Result clearResult = pluginRegistry.resolve("agent-session-clear").orElseThrow()
                    .action().execute(Map.of("session", "s1"), services);
            assertThat(clearResult.isSuccess()).isTrue();

            pluginRegistry.resolve("agent-session-close").orElseThrow()
                    .action().execute(Map.of("session", "s1"), services);
        }
    }

    @Test
    void extractCorpus_parsesPromptResponsePairs() {
        var simulateBlock = Map.<String, Object>of(
                "agent", List.of(
                        Map.of("prompt", "key1", "response", "val1"),
                        Map.of("prompt", "key2", "response", "val2")));

        Map<String, String> corpus = AgentSessionTestSupport.extractCorpus(simulateBlock);
        assertThat(corpus).hasSize(2);
        assertThat(corpus.get("key1")).isEqualTo("val1");
        assertThat(corpus.get("key2")).isEqualTo("val2");
    }

    @Test
    void extractCorpus_parsesConfigMapFormat() {
        var simulateBlock = Map.<String, Object>of(
                "agent", Map.of(
                        "config", Map.of("clearing-threshold", 3),
                        "corpus", List.of(
                                Map.of("prompt", "key1", "response", "val1"))));

        Map<String, String> corpus = AgentSessionTestSupport.extractCorpus(simulateBlock);
        assertThat(corpus).hasSize(1);
        assertThat(corpus.get("key1")).isEqualTo("val1");
    }
}
