package io.casehub.yaml.test;

import io.casehub.platform.simulation.MapSimulationConfig;
import io.casehub.platform.simulation.SimulationConfig;
import io.casehub.platform.simulation.SimulationCorpus;
import io.casehub.platform.simulation.SimulationOverlay;
import io.casehub.platform.simulation.SimulationRuntime;
import io.casehub.platform.simulation.inmem.InMemorySimulationCorpus;
import io.casehub.yaml.plugin.api.Definition;
import io.casehub.yaml.plugin.api.MapServiceRegistry;
import io.casehub.yaml.plugin.api.Result;
import io.casehub.yaml.step.catalog.CompositePluginRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PlaybookRunnerCoreTest {

    private static final Path PLAYBOOK_ROOT = Path.of("src/test/resources/playbooks");

    private CompositePluginRegistry pluginRegistry;
    private MapServiceRegistry serviceRegistry;

    @BeforeEach
    void setUp() {
        pluginRegistry = new CompositePluginRegistry();
        pluginRegistry.register(Definition.of("agent-invoke")
                .execute((params, svc) -> Result.of(Map.of("text", String.valueOf(params.get("prompt")))))
                .build());
        serviceRegistry = new MapServiceRegistry();
    }

    @Test
    void runSimplePlaybookSucceeds() {
        var runner = new PlaybookRunnerCore(pluginRegistry, serviceRegistry,
                PLAYBOOK_ROOT, null);
        var result = runner.run(PLAYBOOK_ROOT.resolve("simple.yaml"),
                PlaybookRunConfig.defaults());

        assertThat(result.error()).as("run error").isNull();
        assertThat(result.success()).isTrue();
        assertThat(result.playbookName()).isEqualTo("Simple playbook");
        assertThat(result.trace()).isNotNull();
        assertThat(result.trace().children()).hasSize(2);
        assertThat(result.durationMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void runPlaybookWithParamsPassesVars() {
        var runner = new PlaybookRunnerCore(pluginRegistry, serviceRegistry,
                PLAYBOOK_ROOT, null);
        var config = new PlaybookRunConfig(false, Map.of("greeting", "hi"), null, null);
        var result = runner.run(PLAYBOOK_ROOT.resolve("with-params.yaml"), config);

        assertThat(result.error()).as("run error").isNull();
        assertThat(result.success()).isTrue();
    }

    @Test
    void validateValidPlaybookReturnsValid() {
        var runner = new PlaybookRunnerCore(pluginRegistry, serviceRegistry,
                PLAYBOOK_ROOT, null);
        var result = runner.validate(PLAYBOOK_ROOT.resolve("simple.yaml"));

        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
    }

    @Test
    void validateInvalidPlaybookReturnsErrors() {
        var runner = new PlaybookRunnerCore(pluginRegistry, serviceRegistry,
                PLAYBOOK_ROOT, null);
        var result = runner.validate(PLAYBOOK_ROOT.resolve("invalid.yaml"));

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).isNotEmpty();
    }

    @Test
    void runTraceRecordsStepOutcomes() {
        var runner = new PlaybookRunnerCore(pluginRegistry, serviceRegistry,
                PLAYBOOK_ROOT, null);
        var result = runner.run(PLAYBOOK_ROOT.resolve("simple.yaml"),
                PlaybookRunConfig.defaults());

        var flat = result.trace().flatten();
        assertThat(flat).hasSizeGreaterThanOrEqualTo(3);
        for (var entry : flat) {
            if (!"playbook".equals(entry.node().type())) {
                assertThat(entry.node().outcome()).isEqualTo(StepOutcome.EXECUTED);
            }
        }
    }

    @Test
    void runNonexistentPlaybookReturnsError() {
        var runner = new PlaybookRunnerCore(pluginRegistry, serviceRegistry,
                PLAYBOOK_ROOT, null);
        var result = runner.run(PLAYBOOK_ROOT.resolve("does-not-exist.yaml"),
                PlaybookRunConfig.defaults());

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isNotNull();
    }

    @Test
    void runPlaybookWithImportsExpandsModuleSteps() {
        var moduleLoader = new FileSystemModuleLoader();
        var runner = new PlaybookRunnerCore(pluginRegistry, serviceRegistry,
                                            PLAYBOOK_ROOT, null, moduleLoader);
        var result = runner.run(PLAYBOOK_ROOT.resolve("with-imports.yaml"),
                                PlaybookRunConfig.defaults());

        assertThat(result.error()).as("run error").isNull();
        assertThat(result.success()).isTrue();
        assertThat(result.trace()).isNotNull();
        assertThat(result.trace().children()).hasSize(3);
    }

    @Test
    void runPlaybookWithoutImportsStillWorks() {
        var moduleLoader = new FileSystemModuleLoader();
        var runner = new PlaybookRunnerCore(pluginRegistry, serviceRegistry,
                                            PLAYBOOK_ROOT, null, moduleLoader);
        var result = runner.run(PLAYBOOK_ROOT.resolve("simple.yaml"),
                                PlaybookRunConfig.defaults());

        assertThat(result.error()).as("run error").isNull();
        assertThat(result.success()).isTrue();
    }

    @Test
    void runWithNullModuleLoaderAndNoImportsWorks() {
        var runner = new PlaybookRunnerCore(pluginRegistry, serviceRegistry,
                                            PLAYBOOK_ROOT, null);
        var result = runner.run(PLAYBOOK_ROOT.resolve("simple.yaml"),
                                PlaybookRunConfig.defaults());

        assertThat(result.error()).as("run error").isNull();
        assertThat(result.success()).isTrue();
    }

    @Test
    void moduleOutputResolvesInVariableReference() {
        var moduleLoader = new FileSystemModuleLoader();
        var runner = new PlaybookRunnerCore(pluginRegistry, serviceRegistry,
                                            PLAYBOOK_ROOT, null, moduleLoader);
        var result = runner.run(PLAYBOOK_ROOT.resolve("with-module-outputs.yaml"),
                                PlaybookRunConfig.defaults());

        assertThat(result.error()).as("run error").isNull();
        assertThat(result.success()).isTrue();
        assertThat(result.trace().children()).hasSize(2);
    }

    @Test
    void multipleImportsWithDifferentAliases() {
        var moduleLoader = new FileSystemModuleLoader();
        var runner = new PlaybookRunnerCore(pluginRegistry, serviceRegistry,
                                            PLAYBOOK_ROOT, null, moduleLoader);
        var result = runner.run(PLAYBOOK_ROOT.resolve("with-multiple-imports.yaml"),
                                PlaybookRunConfig.defaults());

        assertThat(result.error()).as("run error").isNull();
        assertThat(result.success()).isTrue();
        assertThat(result.trace().children()).hasSize(3);
    }

    @Test
    void validatePlaybookWithImportsSucceeds() {
        var moduleLoader = new FileSystemModuleLoader();
        var runner = new PlaybookRunnerCore(pluginRegistry, serviceRegistry,
                                            PLAYBOOK_ROOT, null, moduleLoader);
        var result = runner.validate(PLAYBOOK_ROOT.resolve("with-imports.yaml"));

        assertThat(result.valid()).isTrue();
    }

    @Test
    void moduleOutputResolvesToCorrectValue() {
        var moduleLoader = new FileSystemModuleLoader();
        var runner = new PlaybookRunnerCore(pluginRegistry, serviceRegistry,
                                            PLAYBOOK_ROOT, null, moduleLoader);
        var result = runner.run(PLAYBOOK_ROOT.resolve("with-module-outputs.yaml"),
                                PlaybookRunConfig.defaults());

        assertThat(result.error()).as("run error").isNull();
        assertThat(result.success()).isTrue();
        assertThat(result.output()).containsKey("text");
        assertThat(String.valueOf(result.output().get("text")))
                .contains("claude-3");
    }

    @Test
    void nullModuleLoaderWithImportsThrowsDescriptiveError() {
        var runner = new PlaybookRunnerCore(pluginRegistry, serviceRegistry,
                                            PLAYBOOK_ROOT, null);
        var result = runner.run(PLAYBOOK_ROOT.resolve("with-imports.yaml"),
                                PlaybookRunConfig.defaults());

        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("no ModuleLoader is configured");
    }

    @Test
    void importsAndParamsComposeTogether() {
        var moduleLoader = new FileSystemModuleLoader();
        var runner = new PlaybookRunnerCore(pluginRegistry, serviceRegistry,
                                            PLAYBOOK_ROOT, null, moduleLoader);
        var config = new PlaybookRunConfig(false, Map.of("custom-prompt", "hello world"), null, null);
        var result = runner.run(PLAYBOOK_ROOT.resolve("with-imports-and-params.yaml"), config);

        assertThat(result.error()).as("run error").isNull();
        assertThat(result.success()).isTrue();
        assertThat(result.trace().children()).hasSize(2);
    }

    @Test
    void dryRunDiscoversSiblingSimulationYaml(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("playbook.yaml"),
                          "name: Sim test\nsteps:\n  - agent-invoke:\n      prompt: hello\n    capture: result\n");
        Files.writeString(tempDir.resolve("simulation.yaml"),
                          "methods:\n  agent-provider.invoke:\n    strategy: sequential\n");

        var runtime = new RecordingSimulationRuntime();
        var runner = new PlaybookRunnerCore(pluginRegistry, serviceRegistry,
                                            tempDir, runtime);
        var result = runner.run(tempDir.resolve("playbook.yaml"),
                                PlaybookRunConfig.forDryRun());

        assertThat(result.error()).as("run error").isNull();
        assertThat(result.success()).isTrue();
        assertThat(runtime.lastPushedConfig).isNotNull();
        assertThat(runtime.lastPushedConfig.strategyFor("agent-provider.invoke"))
                .as("discovered simulation.yaml should configure strategy")
                .hasValue("sequential");
    }

    @Test
    void dryRunWalksUpDirectoryHierarchyForSimulationYaml(@TempDir Path tempDir) throws IOException {
        Path subdir = tempDir.resolve("nested/playbooks");
        Files.createDirectories(subdir);
        Files.writeString(subdir.resolve("playbook.yaml"),
                          "name: Nested sim test\nsteps:\n  - agent-invoke:\n      prompt: hello\n    capture: result\n");
        Files.writeString(tempDir.resolve("simulation.yaml"),
                          "methods:\n  agent-provider.invoke:\n    strategy: key\n");

        var runtime = new RecordingSimulationRuntime();
        var runner = new PlaybookRunnerCore(pluginRegistry, serviceRegistry,
                                            subdir, runtime);
        var result = runner.run(subdir.resolve("playbook.yaml"),
                                PlaybookRunConfig.forDryRun());

        assertThat(result.error()).as("run error").isNull();
        assertThat(result.success()).isTrue();
        assertThat(runtime.lastPushedConfig).isNotNull();
        assertThat(runtime.lastPushedConfig.strategyFor("agent-provider.invoke"))
                .as("should walk up to find simulation.yaml in ancestor")
                .hasValue("key");
    }

    @Test
    void dryRunFallsThroughToEmptyOverlayWhenNoSimulationYaml(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("playbook.yaml"),
                          "name: No sim test\nsteps:\n  - agent-invoke:\n      prompt: hello\n    capture: result\n");

        var runtime = new RecordingSimulationRuntime();
        var runner = new PlaybookRunnerCore(pluginRegistry, serviceRegistry,
                                            tempDir, runtime);
        var result = runner.run(tempDir.resolve("playbook.yaml"),
                                PlaybookRunConfig.forDryRun());

        assertThat(result.error()).as("run error").isNull();
        assertThat(result.success()).isTrue();
        assertThat(runtime.lastPushedConfig).isNotNull();
        assertThat(runtime.lastPushedConfig.strategyFor("agent-provider.invoke"))
                .as("no simulation.yaml means empty config")
                .isEmpty();
    }


    static class RecordingSimulationRuntime extends SimulationRuntime {
        SimulationConfig lastPushedConfig;

        RecordingSimulationRuntime() {
            super(MapSimulationConfig.builder().build(), new InMemorySimulationCorpus<>());
        }

        @Override
        public SimulationOverlay pushOverlay(SimulationConfig config, SimulationCorpus corpus) {
            this.lastPushedConfig = config;
            return super.pushOverlay(config, corpus);
        }
    }
}
