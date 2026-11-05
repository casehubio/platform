package io.casehub.yaml.test;

import io.casehub.yaml.plugin.api.Definition;
import io.casehub.yaml.plugin.api.MapServiceRegistry;
import io.casehub.yaml.plugin.api.Result;
import io.casehub.yaml.step.catalog.CompositePluginRegistry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
}
