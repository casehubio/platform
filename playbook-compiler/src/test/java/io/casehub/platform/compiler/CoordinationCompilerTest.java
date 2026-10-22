package io.casehub.platform.compiler;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CoordinationCompilerTest {

    private final PlaybookCompiler compiler = new PlaybookCompiler();

    @Test
    void signalStep_producesSetTaskWithSenderName() {
        var content = Map.<String, Object>of("steps", List.of(
                Map.of("signal", "ready")));

        var outcome = compiler.compile("test", content);
        assertThat(outcome).isInstanceOf(CompilationOutcome.Success.class);

        var result = ((CompilationOutcome.Success) outcome).result();
        assertThat(result.workflow().getDo()).hasSize(1);
        assertThat(result.workflow().getDo().getFirst().getName()).isEqualTo("signal_send_ready");
        assertThat(result.workflow().getDo().getFirst().getTask().getSetTask()).isNotNull();
    }

    @Test
    void awaitSignal_producesSetTaskWithReceiverName() {
        var content = Map.<String, Object>of("steps", List.of(
                Map.of("await", Map.of("signal", "ready"))));

        var outcome = compiler.compile("test", content);
        assertThat(outcome).isInstanceOf(CompilationOutcome.Success.class);

        var result = ((CompilationOutcome.Success) outcome).result();
        assertThat(result.workflow().getDo()).hasSize(1);
        assertThat(result.workflow().getDo().getFirst().getName()).isEqualTo("signal_recv_ready");
    }

    @Test
    void awaitBarrier_producesSetTaskWithBarrierName() {
        var content = Map.<String, Object>of("steps", List.of(
                Map.of("await", Map.of("barrier", "sync"))));

        var outcome = compiler.compile("test", content);
        assertThat(outcome).isInstanceOf(CompilationOutcome.Success.class);

        var result = ((CompilationOutcome.Success) outcome).result();
        assertThat(result.workflow().getDo().getFirst().getName()).startsWith("barrier_sync_");
    }

    @Test
    void awaitWithoutSignalOrBarrier_returnsError() {
        var content = Map.<String, Object>of("steps", List.of(
                Map.of("await", Map.of("unknown", "x"))));

        var outcome = compiler.compile("test", content);
        assertThat(outcome).isInstanceOf(CompilationOutcome.Failure.class);
    }

    @Test
    void orchestrationBlock_producesBarrierSpec() {
        var content = new LinkedHashMap<String, Object>();
        content.put("orchestration", Map.of(
                "barriers", Map.of("sync", Map.of("count", 3))));
        content.put("steps", List.of());

        var outcome = compiler.compile("test", content);
        assertThat(outcome).isInstanceOf(CompilationOutcome.Success.class);

        var result = ((CompilationOutcome.Success) outcome).result();
        assertThat(result.coordinationSpec().primitives()).hasSize(1);
        assertThat(result.coordinationSpec().primitives().getFirst())
                .isInstanceOf(CoordinationSpec.BarrierSpec.class);
        var barrier = (CoordinationSpec.BarrierSpec) result.coordinationSpec().primitives().getFirst();
        assertThat(barrier.required()).isEqualTo(3);
    }

    @Test
    void orchestrationBlock_producesSignalSpecs() {
        var content = new LinkedHashMap<String, Object>();
        content.put("orchestration", Map.of(
                "signals", List.of("ready", "ack")));
        content.put("steps", List.of());

        var outcome = compiler.compile("test", content);
        assertThat(outcome).isInstanceOf(CompilationOutcome.Success.class);

        var result = ((CompilationOutcome.Success) outcome).result();
        assertThat(result.coordinationSpec().primitives()).hasSize(2);
        assertThat(result.coordinationSpec().primitives().stream()
                .allMatch(p -> p instanceof CoordinationSpec.SignalSpec)).isTrue();
    }

    @Test
    void concurrentWithSignals_compilesFullOrchestration() {
        var content = new LinkedHashMap<String, Object>();
        content.put("orchestration", Map.of("signals", List.of("go")));

        var branches = new LinkedHashMap<String, Object>();
        branches.put("sender", List.of(
                Map.of("log", Map.of("message", "init")),
                Map.of("signal", "go")));
        branches.put("receiver", List.of(
                Map.of("await", Map.of("signal", "go")),
                Map.of("log", Map.of("message", "proceed"))));
        content.put("steps", List.of(Map.of("concurrent", branches)));

        var outcome = compiler.compile("test", content);
        assertThat(outcome).isInstanceOf(CompilationOutcome.Success.class);

        var result = ((CompilationOutcome.Success) outcome).result();
        assertThat(result.workflow().getDo()).hasSize(1);
        var fork = result.workflow().getDo().getFirst().getTask().getForkTask();
        assertThat(fork.getFork().getBranches()).hasSize(2);
        assertThat(result.coordinationSpec().primitives()).hasSize(1);
    }
}
