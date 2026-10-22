package io.casehub.platform.compiler;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EndToEndCompilationTest {

    private final PlaybookCompiler compiler = new PlaybookCompiler();

    @Test
    void orchestratedPlaybook_compilesWithCoordination() {
        var content = new LinkedHashMap<String, Object>();
        content.put("playbook", "barrier-demo");
        content.put("orchestration", Map.of(
                "barriers", Map.of("all-ready", Map.of("count", 2)),
                "signals", List.of("go")));

        var branches = new LinkedHashMap<String, Object>();
        branches.put("init", List.of(
                Map.of("rest-request", Map.of("method", "GET", "url", "https://api.example.com/init")),
                Map.of("signal", "go")));
        branches.put("worker", List.of(
                Map.of("await", Map.of("signal", "go")),
                Map.of("rest-request", Map.of("method", "POST", "url", "https://api.example.com/process"))));
        content.put("steps", List.of(Map.of("concurrent", branches)));

        var outcome = compiler.compile("barrier-demo", content);
        assertThat(outcome).isInstanceOf(CompilationOutcome.Success.class);

        var result = ((CompilationOutcome.Success) outcome).result();

        assertThat(result.workflow().getDo()).hasSize(1);
        var fork = result.workflow().getDo().getFirst().getTask().getForkTask();
        assertThat(fork).isNotNull();
        assertThat(fork.getFork().getBranches()).hasSize(2);

        assertThat(result.coordinationSpec().primitives()).hasSize(2);
        assertThat(result.sourceMap().entries()).isNotEmpty();
    }

    @Test
    void decoratedRestRequest_nestingOrder_whenTimeoutRetryCoreF() {
        var step = new LinkedHashMap<String, Object>();
        step.put("rest-request", Map.of("method", "POST", "url", "https://api.example.com/orders"));
        step.put("retry", 3);
        step.put("timeout", "10s");
        step.put("when", "${order.valid}");

        var content = Map.<String, Object>of("steps", List.of(step));
        var outcome = compiler.compile("test", content);
        assertThat(outcome).isInstanceOf(CompilationOutcome.Success.class);

        var result = ((CompilationOutcome.Success) outcome).result();
        assertThat(result.workflow().getDo()).hasSize(1);
        var outerTask = result.workflow().getDo().getFirst().getTask();
        assertThat(outerTask.getSwitchTask()).isNotNull();
    }

    @Test
    void nestedPlaybook_deepRecursion() {
        var content = Map.<String, Object>of("steps", List.of(
                Map.of("block", List.of(
                        Map.of("parallel", List.of(
                                Map.of("log", Map.of("message", "a")),
                                Map.of("delay", "100ms"))),
                        Map.of("log", Map.of("message", "done"))))));

        var outcome = compiler.compile("test", content);
        assertThat(outcome).isInstanceOf(CompilationOutcome.Success.class);

        var result = ((CompilationOutcome.Success) outcome).result();
        var block = result.workflow().getDo().getFirst().getTask().getDoTask();
        assertThat(block).isNotNull();
        assertThat(block.getDo()).hasSize(2);
        assertThat(block.getDo().getFirst().getTask().getForkTask()).isNotNull();
    }

    @Test
    void structuralDecorators_timeoutWrapsBlock() {
        var step = new LinkedHashMap<String, Object>();
        step.put("timeout 10s", List.of(
                Map.of("log", Map.of("message", "a")),
                Map.of("log", Map.of("message", "b"))));

        var content = Map.<String, Object>of("steps", List.of(step));
        var outcome = compiler.compile("test", content);
        assertThat(outcome).isInstanceOf(CompilationOutcome.Success.class);

        var result = ((CompilationOutcome.Success) outcome).result();
        var task = result.workflow().getDo().getFirst().getTask();
        assertThat(task.getDoTask()).isNotNull();
        assertThat(task.getDoTask().getDo()).hasSize(2);
    }

    @Test
    void structuralDecorators_retryWrapsBlock() {
        var step = new LinkedHashMap<String, Object>();
        step.put("retry 3", List.of(
                Map.of("log", Map.of("message", "a")),
                Map.of("log", Map.of("message", "b"))));

        var content = Map.<String, Object>of("steps", List.of(step));
        var outcome = compiler.compile("test", content);
        assertThat(outcome).isInstanceOf(CompilationOutcome.Success.class);

        var result = ((CompilationOutcome.Success) outcome).result();
        var outerTask = result.workflow().getDo().getFirst().getTask();
        assertThat(outerTask.getTryTask()).isNotNull();
    }

    @Test
    void mutexDecorator_addsSemaphoreToCoordinationSpec() {
        var step = new LinkedHashMap<String, Object>();
        step.put("rest-request", Map.of("method", "POST", "url", "https://api.example.com/write"));
        step.put("mutex", "db-write");

        var content = Map.<String, Object>of("steps", List.of(step));
        var outcome = compiler.compile("test", content);
        assertThat(outcome).isInstanceOf(CompilationOutcome.Success.class);

        var result = ((CompilationOutcome.Success) outcome).result();
        assertThat(result.coordinationSpec().isEmpty()).isFalse();
        var sem = (CoordinationSpec.SemaphoreSpec) result.coordinationSpec().primitives().getFirst();
        assertThat(sem.name()).isEqualTo("db-write");
        assertThat(sem.maxPermits()).isEqualTo(1);
    }

    @Test
    void complexPlaybook_allStepTypes() {
        var ifStep = new LinkedHashMap<String, Object>();
        ifStep.put("if", "${enabled}");
        ifStep.put("then", List.of(Map.of("log", Map.of("message", "on"))));
        ifStep.put("else", List.of(Map.of("log", Map.of("message", "off"))));

        var tryStep = new LinkedHashMap<String, Object>();
        tryStep.put("try", List.of(
                Map.of("rest-request", Map.of("method", "POST", "url", "https://api.example.com/action"))));
        tryStep.put("catch", List.of(Map.of("log", Map.of("message", "failed"))));

        var content = new LinkedHashMap<String, Object>();
        content.put("steps", List.of(
                Map.of("log", Map.of("message", "start")),
                ifStep,
                Map.of("parallel", List.of(
                        Map.of("rest-request", Map.of("method", "GET", "url", "https://api.example.com/a")),
                        Map.of("rest-request", Map.of("method", "GET", "url", "https://api.example.com/b")))),
                tryStep,
                Map.of("forEach", Map.of("in", "items", "as", "item")),
                Map.of("delay", "1s"),
                Map.of("log", Map.of("message", "done"))));

        var outcome = compiler.compile("complex", content);
        assertThat(outcome).isInstanceOf(CompilationOutcome.Success.class);

        var result = ((CompilationOutcome.Success) outcome).result();
        assertThat(result.workflow().getDo()).hasSize(7);
        assertThat(result.workflow().getDocument().getName()).isEqualTo("complex");

        assertThat(result.workflow().getDo().get(0).getTask().getSetTask()).isNotNull();
        assertThat(result.workflow().getDo().get(1).getTask().getSwitchTask()).isNotNull();
        assertThat(result.workflow().getDo().get(2).getTask().getForkTask()).isNotNull();
        assertThat(result.workflow().getDo().get(3).getTask().getTryTask()).isNotNull();
        assertThat(result.workflow().getDo().get(4).getTask().getForTask()).isNotNull();
        assertThat(result.workflow().getDo().get(5).getTask().getWaitTask()).isNotNull();
        assertThat(result.workflow().getDo().get(6).getTask().getSetTask()).isNotNull();
    }
}
