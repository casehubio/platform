package io.casehub.platform.compiler;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AdvisoryCompilerTest {

    private final PlaybookCompiler compiler = new PlaybookCompiler();

    @Test
    void advisory_compilesToSetTaskWithAdvisoryProperties() {
        var content = Map.<String, Object>of("steps", List.of(
                Map.of("advisory", Map.of(
                        "severity", "WARNING",
                        "message", "channel capacity exceeded",
                        "action", "ESCALATE"))));

        var outcome = compiler.compile("test", content);
        assertThat(outcome).isInstanceOf(CompilationOutcome.Success.class);

        var result = ((CompilationOutcome.Success) outcome).result();
        assertThat(result.workflow().getDo()).hasSize(1);

        var taskItem = result.workflow().getDo().getFirst();
        assertThat(taskItem.getName()).startsWith("advisory_");

        var setTask = taskItem.getTask().getSetTask();
        assertThat(setTask).isNotNull();

        var props = setTask.getSet().getSetTaskConfiguration().getAdditionalProperties();
        assertThat(props).containsEntry("severity", "WARNING");
        assertThat(props).containsEntry("message", "channel capacity exceeded");
        assertThat(props).containsEntry("action", "ESCALATE");
    }

    @Test
    void advisory_respectsWhenDecorator() {
        var step = new LinkedHashMap<String, Object>();
        step.put("advisory", Map.of(
                "severity", "CRITICAL",
                "message", "threshold breached",
                "action", "REROUTE"));
        step.put("when", "${openCommands >= config.maxOpenCommands}");

        var content = Map.<String, Object>of("steps", List.of(step));
        var outcome = compiler.compile("test", content);
        assertThat(outcome).isInstanceOf(CompilationOutcome.Success.class);

        var result = ((CompilationOutcome.Success) outcome).result();
        var task = result.workflow().getDo().getFirst().getTask();
        assertThat(task.getSwitchTask()).isNotNull();
    }

    @Test
    void advisory_recordsSourceMap() {
        var content = Map.<String, Object>of("steps", List.of(
                Map.of("advisory", Map.of(
                        "severity", "INFO",
                        "message", "status update",
                        "action", "LOG"))));

        var outcome = compiler.compile("test", content);
        assertThat(outcome).isInstanceOf(CompilationOutcome.Success.class);

        var result = ((CompilationOutcome.Success) outcome).result();
        assertThat(result.sourceMap().entries()).hasSize(1);
    }

    @Test
    void advisory_missingRequiredField_producesError() {
        var content = Map.<String, Object>of("steps", List.of(
                Map.of("advisory", Map.of("severity", "INFO", "action", "LOG"))));

        var outcome = compiler.compile("test", content);
        assertThat(outcome).isInstanceOf(CompilationOutcome.Failure.class);

        var diagnostics = ((CompilationOutcome.Failure) outcome).diagnostics();
        assertThat(diagnostics).isNotEmpty();
        assertThat(diagnostics.getFirst().message()).contains("message");
    }

    @Test
    void advisory_stringShorthand_producesError() {
        var content = Map.<String, Object>of("steps", List.of(
                Map.of("advisory", "just a string")));

        var outcome = compiler.compile("test", content);
        assertThat(outcome).isInstanceOf(CompilationOutcome.Failure.class);
    }
}
