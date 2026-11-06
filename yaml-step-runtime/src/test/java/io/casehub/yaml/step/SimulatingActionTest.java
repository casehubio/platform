package io.casehub.yaml.step;

import io.casehub.platform.simulation.MapSimulationConfig;
import io.casehub.platform.simulation.SimulationRuntime;
import io.casehub.platform.simulation.inmem.InMemorySimulationCorpus;
import io.casehub.yaml.core.step.InvokeBinding;
import io.casehub.yaml.plugin.api.Action;
import io.casehub.yaml.plugin.api.Result;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SimulatingActionTest {

    @Test
    void delegatesToRealAction_whenNoOverlayActive() {
        var runtime = new SimulationRuntime(
                MapSimulationConfig.builder().build(),
                new InMemorySimulationCorpus<>());
        Action real = (params, services) -> Result.of(Map.of("real", true), Map.of());
        var binding = new InvokeBinding.Rest("GET", "/api/users", Map.of(), Map.of());

        var simulating = new SimulatingAction(real, binding, runtime);
        var result = simulating.execute(Map.of(), null);

        assertThat(result).isInstanceOf(Result.Success.class);
        assertThat(((Result.Success) result).output()).containsEntry("real", true);
    }

    @Test
    void returnsCorpusResponse_whenOverlayHasMatch() {
        var baseCorpus = new InMemorySimulationCorpus<>();
        var runtime = new SimulationRuntime(
                MapSimulationConfig.builder().build(), baseCorpus);

        var overlayCorpus = new InMemorySimulationCorpus<>();
        overlayCorpus.record("rest-request", null, "/api/users",
                Map.of("url", "/api/users"),
                Map.of("users", List.of(Map.of("id", 1))));

        var overlayConfig = MapSimulationConfig.builder()
                .strategy("rest-request", "key-lookup")
                .build();
        runtime.pushOverlay(overlayConfig, overlayCorpus);

        Action real = (params, services) -> Result.of(Map.of("real", true), Map.of());
        var binding = new InvokeBinding.Rest("GET", "/api/users", Map.of(), Map.of());
        var simulating = new SimulatingAction(real, binding, runtime);

        var result = simulating.execute(Map.of(), null);
        assertThat(result).isInstanceOf(Result.Success.class);
        var output = ((Result.Success) result).output();
        assertThat(output).containsKey("users");
        assertThat(output).doesNotContainKey("real");
    }

    @Test
    void delegatesToRealAction_whenOverlayHasNoMatch() {
        var runtime = new SimulationRuntime(
                MapSimulationConfig.builder().build(),
                new InMemorySimulationCorpus<>());

        var overlayConfig = MapSimulationConfig.builder()
                .strategy("rest-request", "key-lookup")
                .build();
        runtime.pushOverlay(overlayConfig, new InMemorySimulationCorpus<>());

        Action real = (params, services) -> Result.of(Map.of("real", true), Map.of());
        var binding = new InvokeBinding.Rest("GET", "/api/users", Map.of(), Map.of());
        var simulating = new SimulatingAction(real, binding, runtime);

        var result = simulating.execute(Map.of(), null);
        assertThat(result).isInstanceOf(Result.Success.class);
        assertThat(((Result.Success) result).output()).containsEntry("real", true);
    }

    @Test
    void worksWithGraphqlBinding() {
        var baseCorpus = new InMemorySimulationCorpus<>();
        var runtime = new SimulationRuntime(
                MapSimulationConfig.builder().build(), baseCorpus);

        var overlayCorpus = new InMemorySimulationCorpus<>();
        overlayCorpus.record("graphql-exec", null, "{ users { id } }",
                Map.of("query", "{ users { id } }"),
                Map.of("users", List.of(Map.of("id", 1))));

        runtime.pushOverlay(
                MapSimulationConfig.builder().strategy("graphql-exec", "key-lookup").build(),
                overlayCorpus);

        Action real = (params, services) -> Result.of(Map.of("real", true), Map.of());
        var binding = new InvokeBinding.Graphql("{ users { id } }");
        var simulating = new SimulatingAction(real, binding, runtime);

        var result = simulating.execute(Map.of(), null);
        assertThat(result).isInstanceOf(Result.Success.class);
        assertThat(((Result.Success) result).output()).containsKey("users");
    }

    @Test
    void worksWithMcpBinding() {
        var baseCorpus = new InMemorySimulationCorpus<>();
        var runtime = new SimulationRuntime(
                MapSimulationConfig.builder().build(), baseCorpus);

        var overlayCorpus = new InMemorySimulationCorpus<>();
        overlayCorpus.record("mcp-call", null, "file_search",
                Map.of("tool", "file_search"),
                Map.of("results", List.of("auth.ts")));

        runtime.pushOverlay(
                MapSimulationConfig.builder().strategy("mcp-call", "key-lookup").build(),
                overlayCorpus);

        Action real = (params, services) -> Result.of(Map.of("real", true), Map.of());
        var binding = new InvokeBinding.Mcp("file_search");
        var simulating = new SimulatingAction(real, binding, runtime);

        var result = simulating.execute(Map.of(), null);
        assertThat(result).isInstanceOf(Result.Success.class);
        assertThat(((Result.Success) result).output()).containsKey("results");
    }
}
