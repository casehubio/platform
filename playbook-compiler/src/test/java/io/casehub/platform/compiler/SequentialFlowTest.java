package io.casehub.platform.compiler;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SequentialFlowTest {

    private final PlaybookCompiler compiler = new PlaybookCompiler();

    @Test
    void block_compilesToDoTask() {
        var content = Map.<String, Object>of("steps", List.of(
                Map.of("block", List.of(
                        Map.of("log", Map.of("message", "a")),
                        Map.of("log", Map.of("message", "b"))))));

        var outcome = compiler.compile("test", content);
        assertThat(outcome).isInstanceOf(CompilationOutcome.Success.class);

        var result = ((CompilationOutcome.Success) outcome).result();
        var task = result.workflow().getDo().getFirst().getTask();
        assertThat(task.getDoTask()).isNotNull();
        assertThat(task.getDoTask().getDo()).hasSize(2);
    }

    @Test
    void delay_compilesToWaitTask() {
        var content = Map.<String, Object>of("steps", List.of(
                Map.of("delay", "1000ms")));

        var outcome = compiler.compile("test", content);
        assertThat(outcome).isInstanceOf(CompilationOutcome.Success.class);

        var result = ((CompilationOutcome.Success) outcome).result();
        var task = result.workflow().getDo().getFirst().getTask();
        assertThat(task.getWaitTask()).isNotNull();
        assertThat(task.getWaitTask().getWait()).isNotNull();
    }

    @Test
    void delay_seconds_compilesCorrectly() {
        var content = Map.<String, Object>of("steps", List.of(
                Map.of("delay", "2s")));

        var outcome = compiler.compile("test", content);
        assertThat(outcome).isInstanceOf(CompilationOutcome.Success.class);

        var result = ((CompilationOutcome.Success) outcome).result();
        var wait = result.workflow().getDo().getFirst().getTask().getWaitTask();
        assertThat(wait.getWait().getDurationInline()).isNotNull();
        assertThat(wait.getWait().getDurationInline().getSeconds()).isEqualTo(2);
    }

    @Test
    void nestedBlocks_compileRecursively() {
        var content = Map.<String, Object>of("steps", List.of(
                Map.of("block", List.of(
                        Map.of("block", List.of(
                                Map.of("log", Map.of("message", "inner"))))))));

        var outcome = compiler.compile("test", content);
        assertThat(outcome).isInstanceOf(CompilationOutcome.Success.class);

        var result = ((CompilationOutcome.Success) outcome).result();
        var outer = result.workflow().getDo().getFirst().getTask().getDoTask();
        assertThat(outer.getDo()).hasSize(1);
        var inner = outer.getDo().getFirst().getTask().getDoTask();
        assertThat(inner).isNotNull();
        assertThat(inner.getDo()).hasSize(1);
    }
}
