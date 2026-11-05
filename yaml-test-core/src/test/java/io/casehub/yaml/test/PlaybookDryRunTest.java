package io.casehub.yaml.test;

import io.casehub.platform.simulation.InvocationRecord;
import io.casehub.platform.simulation.MapSimulationConfig;
import io.casehub.platform.simulation.SimulationRuntime;
import io.casehub.platform.simulation.inmem.InMemorySimulationCorpus;
import io.casehub.yaml.plugin.api.Definition;
import io.casehub.yaml.plugin.api.MapServiceRegistry;
import io.casehub.yaml.plugin.api.Result;
import io.casehub.yaml.step.catalog.CompositePluginRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PlaybookDryRunTest {

    private static final Path PLAYBOOK_ROOT = Path.of("src/test/playbooks");

    private CompositePluginRegistry pluginRegistry;
    private MapServiceRegistry serviceRegistry;
    private SimulationRuntime simulationRuntime;

    @BeforeEach
    void setUp() {
        pluginRegistry = new CompositePluginRegistry();
        pluginRegistry.register(Definition.of("agent-invoke")
                .execute((params, svc) -> Result.of(Map.of("text", String.valueOf(params.get("prompt")))))
                .build());

        serviceRegistry = new MapServiceRegistry();

        var corpus = new InMemorySimulationCorpus<Object, Object>();
        corpus.seed("agent-invoke", List.of(
                InvocationRecord.of("default", "hello", "hello", "simulated response"),
                InvocationRecord.of("default", "world", "world", "simulated world")));

        simulationRuntime = new SimulationRuntime(
                MapSimulationConfig.builder()
                        .strategy("agent-invoke", "sequential")
                        .build(),
                corpus);
    }

    @Test
    void dryRunDetectsSimulatedSteps() {
        var runner = new PlaybookRunnerCore(pluginRegistry, serviceRegistry,
                PLAYBOOK_ROOT, simulationRuntime, new FileSystemModuleLoader());
        var result = runner.run(PLAYBOOK_ROOT.resolve("simple.yaml"),
                PlaybookRunConfig.forDryRun());

        assertThat(result.success()).isTrue();
        assertThat(result.trace()).isNotNull();

        var children = result.trace().children();
        assertThat(children).isNotEmpty();
        assertThat(children).allMatch(c -> c.outcome() == StepOutcome.SIMULATED);
        assertThat(children.get(0).detail()).containsKey("strategy");
    }

    @Test
    void nonDryRunDoesNotMarkSimulated() {
        var runner = new PlaybookRunnerCore(pluginRegistry, serviceRegistry,
                PLAYBOOK_ROOT, simulationRuntime, new FileSystemModuleLoader());
        var result = runner.run(PLAYBOOK_ROOT.resolve("simple.yaml"),
                PlaybookRunConfig.defaults());

        assertThat(result.success()).isTrue();
        var children = result.trace().children();
        assertThat(children).allMatch(c -> c.outcome() == StepOutcome.EXECUTED);
    }
}
