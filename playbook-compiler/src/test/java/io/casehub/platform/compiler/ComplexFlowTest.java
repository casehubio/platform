package io.casehub.platform.compiler;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ComplexFlowTest {

    private final PlaybookCompiler compiler = new PlaybookCompiler();

    @Test
    void tryCatch_compilesToTryTask() {
        var step = new LinkedHashMap<String, Object>();
        step.put("try", List.of(Map.of("log", Map.of("message", "try"))));
        step.put("catch", List.of(Map.of("log", Map.of("message", "catch"))));

        var content = Map.<String, Object>of("steps", List.of(step));
        var outcome = compiler.compile("test", content);
        assertThat(outcome).isInstanceOf(CompilationOutcome.Success.class);

        var result = ((CompilationOutcome.Success) outcome).result();
        var task = result.workflow().getDo().getFirst().getTask();
        assertThat(task.getTryTask()).isNotNull();
        assertThat(task.getTryTask().getTry()).isNotEmpty();
        assertThat(task.getTryTask().getCatch().getDo()).isNotEmpty();
    }

    @Test
    void tryCatch_withoutCatch_producesEmptyCatchBlock() {
        var step = new LinkedHashMap<String, Object>();
        step.put("try", List.of(Map.of("log", Map.of("message", "try"))));

        var content = Map.<String, Object>of("steps", List.of(step));
        var outcome = compiler.compile("test", content);
        assertThat(outcome).isInstanceOf(CompilationOutcome.Success.class);

        var result = ((CompilationOutcome.Success) outcome).result();
        var tryTask = result.workflow().getDo().getFirst().getTask().getTryTask();
        assertThat(tryTask.getCatch().getDo()).isEmpty();
    }

    @Test
    void forEach_compilesToForTask() {
        var step = new LinkedHashMap<String, Object>();
        step.put("forEach", Map.of("in", "items", "as", "item"));

        var content = Map.<String, Object>of("steps", List.of(step));
        var outcome = compiler.compile("test", content);
        assertThat(outcome).isInstanceOf(CompilationOutcome.Success.class);

        var result = ((CompilationOutcome.Success) outcome).result();
        var task = result.workflow().getDo().getFirst().getTask();
        assertThat(task.getForTask()).isNotNull();
        assertThat(task.getForTask().getFor().getEach()).isEqualTo("item");
    }

    @Test
    void parallel_compilesToForkNotCompete() {
        var content = Map.<String, Object>of("steps", List.of(
                Map.of("parallel", List.of(
                        Map.of("log", Map.of("message", "a")),
                        Map.of("log", Map.of("message", "b"))))));

        var outcome = compiler.compile("test", content);
        assertThat(outcome).isInstanceOf(CompilationOutcome.Success.class);

        var result = ((CompilationOutcome.Success) outcome).result();
        var task = result.workflow().getDo().getFirst().getTask();
        assertThat(task.getForkTask()).isNotNull();
        assertThat(task.getForkTask().getFork().isCompete()).isFalse();
        assertThat(task.getForkTask().getFork().getBranches()).hasSize(2);
    }

    @Test
    void concurrent_namedBranches_compilesToFork() {
        var branches = new LinkedHashMap<String, Object>();
        branches.put("fast", List.of(Map.of("log", Map.of("message", "a"))));
        branches.put("slow", List.of(Map.of("log", Map.of("message", "b"))));

        var content = Map.<String, Object>of("steps", List.of(Map.of("concurrent", branches)));

        var outcome = compiler.compile("test", content);
        assertThat(outcome).isInstanceOf(CompilationOutcome.Success.class);

        var result = ((CompilationOutcome.Success) outcome).result();
        var fork = result.workflow().getDo().getFirst().getTask().getForkTask();
        assertThat(fork.getFork().getBranches()).hasSize(2);
        assertThat(fork.getFork().getBranches().getFirst().getName()).contains("fast");
    }

    @Test
    void race_compilesToForkCompete() {
        var content = Map.<String, Object>of("steps", List.of(
                Map.of("race", List.of(
                        Map.of("log", Map.of("message", "a")),
                        Map.of("log", Map.of("message", "b"))))));

        var outcome = compiler.compile("test", content);
        assertThat(outcome).isInstanceOf(CompilationOutcome.Success.class);

        var result = ((CompilationOutcome.Success) outcome).result();
        var fork = result.workflow().getDo().getFirst().getTask().getForkTask();
        assertThat(fork).isNotNull();
        assertThat(fork.getFork().isCompete()).isTrue();
    }
}
