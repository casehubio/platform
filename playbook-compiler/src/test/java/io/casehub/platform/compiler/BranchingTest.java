package io.casehub.platform.compiler;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BranchingTest {

    private final PlaybookCompiler compiler = new PlaybookCompiler();

    @Test
    void ifElse_compilesToSwitchWithTwoCases() {
        var step = new LinkedHashMap<String, Object>();
        step.put("if", "true");
        step.put("then", List.of(Map.of("log", Map.of("message", "yes"))));
        step.put("else", List.of(Map.of("log", Map.of("message", "no"))));

        var content = Map.<String, Object>of("steps", List.of(step));
        var outcome = compiler.compile("test", content);
        assertThat(outcome).isInstanceOf(CompilationOutcome.Success.class);

        var result = ((CompilationOutcome.Success) outcome).result();
        var task = result.workflow().getDo().getFirst().getTask();
        assertThat(task.getSwitchTask()).isNotNull();
        assertThat(task.getSwitchTask().getSwitch()).hasSize(2);
    }

    @Test
    void ifWithoutElse_compilesToSwitchWithOneCase() {
        var step = new LinkedHashMap<String, Object>();
        step.put("if", "${order.valid}");
        step.put("then", List.of(Map.of("log", Map.of("message", "valid"))));

        var content = Map.<String, Object>of("steps", List.of(step));
        var outcome = compiler.compile("test", content);
        assertThat(outcome).isInstanceOf(CompilationOutcome.Success.class);

        var result = ((CompilationOutcome.Success) outcome).result();
        var switchTask = result.workflow().getDo().getFirst().getTask().getSwitchTask();
        assertThat(switchTask.getSwitch()).hasSize(1);
        assertThat(switchTask.getSwitch().getFirst().getSwitchCase().getWhen()).isEqualTo("${order.valid}");
    }

    @Test
    void match_compilesToSwitchWithMultipleCases() {
        var step = new LinkedHashMap<String, Object>();
        step.put("match", "warning");
        step.put("cases", List.of(
                Map.of("pattern", "error", "log", Map.of("message", "Matched: error")),
                Map.of("pattern", "warning", "log", Map.of("message", "Matched: warning")),
                Map.of("default", List.of(Map.of("log", Map.of("message", "Default"))))));

        var content = Map.<String, Object>of("steps", List.of(step));
        var outcome = compiler.compile("test", content);
        assertThat(outcome).isInstanceOf(CompilationOutcome.Success.class);

        var result = ((CompilationOutcome.Success) outcome).result();
        var switchTask = result.workflow().getDo().getFirst().getTask().getSwitchTask();
        assertThat(switchTask.getSwitch()).hasSize(3);
    }
}
