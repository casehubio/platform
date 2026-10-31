package io.casehub.platform.compiler;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PlaybookCompilerTest {

    private final PlaybookCompiler compiler = new PlaybookCompiler();

    @Test
    void compile_emptySteps_returnsSuccessWithEmptyWorkflow() {
        var content = Map.<String, Object>of("playbook", "empty-test", "steps", List.of());
        CompilationOutcome outcome = compiler.compile("empty", content);
        assertThat(outcome).isInstanceOf(CompilationOutcome.Success.class);

        var result = ((CompilationOutcome.Success) outcome).result();
        assertThat(result.workflow().getDo()).isEmpty();
        assertThat(result.coordinationSpec().isEmpty()).isTrue();
    }

    @Test
    void compile_unknownStepType_returnsFailure() {
        var content = Map.<String, Object>of("steps", List.of(Map.of("unknown-action", "value")));
        CompilationOutcome outcome = compiler.compile("test", content);
        assertThat(outcome).isInstanceOf(CompilationOutcome.Failure.class);

        var diagnostics = ((CompilationOutcome.Failure) outcome).diagnostics();
        assertThat(diagnostics).hasSize(1);
        assertThat(diagnostics.getFirst().severity()).isEqualTo(CompilationDiagnostic.Severity.ERROR);
    }

    @Test
    void compile_logStep_producesSetTask() {
        var content = Map.<String, Object>of("steps", List.of(
                Map.of("log", Map.of("message", "hello"))));
        CompilationOutcome outcome = compiler.compile("test", content);
        assertThat(outcome).isInstanceOf(CompilationOutcome.Success.class);

        var result = ((CompilationOutcome.Success) outcome).result();
        assertThat(result.workflow().getDo()).hasSize(1);
        assertThat(result.workflow().getDo().getFirst().getTask().getSetTask()).isNotNull();
    }

    @Test
    void compile_decoratedStep_whenGuardWrapsInSwitch() {
        var step = new LinkedHashMap<String, Object>();
        step.put("log", Map.of("message", "hello"));
        step.put("when", "true");

        var content = Map.<String, Object>of("steps", List.of(step));
        CompilationOutcome outcome = compiler.compile("test", content);
        assertThat(outcome).isInstanceOf(CompilationOutcome.Success.class);

        var result = ((CompilationOutcome.Success) outcome).result();
        var firstTask = result.workflow().getDo().getFirst().getTask();
        assertThat(firstTask.getSwitchTask()).isNotNull();
    }

    @Test
    void compile_decoratedStep_retryWrapsInTryTask() {
        var step = new LinkedHashMap<String, Object>();
        step.put("log", Map.of("message", "hello"));
        step.put("retry", 3);

        var content = Map.<String, Object>of("steps", List.of(step));
        CompilationOutcome outcome = compiler.compile("test", content);
        assertThat(outcome).isInstanceOf(CompilationOutcome.Success.class);

        var result = ((CompilationOutcome.Success) outcome).result();
        var firstTask = result.workflow().getDo().getFirst().getTask();
        assertThat(firstTask.getTryTask()).isNotNull();
        assertThat(firstTask.getTryTask().getTry()).hasSize(1);
    }

    @Test
    void compile_mutexDecorator_addsToCoordinationSpec() {
        var step = new LinkedHashMap<String, Object>();
        step.put("log", Map.of("message", "hello"));
        step.put("mutex", "db-write");

        var content = Map.<String, Object>of("steps", List.of(step));
        CompilationOutcome outcome = compiler.compile("test", content);
        assertThat(outcome).isInstanceOf(CompilationOutcome.Success.class);

        var result = ((CompilationOutcome.Success) outcome).result();
        assertThat(result.coordinationSpec().isEmpty()).isFalse();
        assertThat(result.coordinationSpec().primitives().getFirst())
                .isInstanceOf(CoordinationSpec.SemaphoreSpec.class);
    }

    @Test
    void compile_orchestrationBlock_producesBarrierAndSignalSpecs() {
        var content = new LinkedHashMap<String, Object>();
        content.put("orchestration", Map.of(
                "barriers", Map.of("sync", Map.of("count", 3)),
                "signals", List.of("ready", "ack")));
        content.put("steps", List.of());

        CompilationOutcome outcome = compiler.compile("test", content);
        assertThat(outcome).isInstanceOf(CompilationOutcome.Success.class);

        var result = ((CompilationOutcome.Success) outcome).result();
        assertThat(result.coordinationSpec().primitives()).hasSize(3);
    }

    @Test
    void compile_sourceMapPopulated() {
        var content = Map.<String, Object>of("steps", List.of(
                Map.of("log", Map.of("message", "a")),
                Map.of("log", Map.of("message", "b"))));

        CompilationOutcome outcome = compiler.compile("test", content);
        assertThat(outcome).isInstanceOf(CompilationOutcome.Success.class);

        var result = ((CompilationOutcome.Success) outcome).result();
        assertThat(result.sourceMap().entries()).hasSize(2);
    }

    @Test
    void compile_structuralTimeoutKey_resolvesToBlockWithTimeout() {
        var step = new LinkedHashMap<String, Object>();
        step.put("timeout 5s", List.of(Map.of("log", Map.of("message", "inside"))));

        var content = Map.<String, Object>of("steps", List.of(step));
        CompilationOutcome outcome = compiler.compile("test", content);
        assertThat(outcome).isInstanceOf(CompilationOutcome.Success.class);

        var result = ((CompilationOutcome.Success) outcome).result();
        var task = result.workflow().getDo().getFirst().getTask();
        assertThat(task.getDoTask()).isNotNull();
    }

    @Test
    void compile_agentSessionSteps_producesSetTasks() {
        var content = Map.<String, Object>of("steps", List.of(
                Map.of("agent-session-open", Map.of("session", "analyst", "system", "You are an analyst")),
                Map.of("agent-query", Map.of("session", "analyst", "user", "Summarize this")),
                Map.of("agent-session-clear", Map.of("session", "analyst")),
                Map.of("agent-session-close", Map.of("session", "analyst"))));
        CompilationOutcome outcome = compiler.compile("test", content);
        assertThat(outcome).isInstanceOf(CompilationOutcome.Success.class);

        var result = ((CompilationOutcome.Success) outcome).result();
        assertThat(result.workflow().getDo()).hasSize(4);
        for (var item : result.workflow().getDo()) {
            assertThat(item.getTask().getSetTask()).isNotNull();
        }
    }

    @Test
    void compile_agentSessionOpen_missingSession_fails() {
        var content = Map.<String, Object>of("steps", List.of(
                Map.of("agent-session-open", Map.of("system", "You are an analyst"))));
        CompilationOutcome outcome = compiler.compile("test", content);
        assertThat(outcome).isInstanceOf(CompilationOutcome.Failure.class);
        var diagnostics = ((CompilationOutcome.Failure) outcome).diagnostics();
        assertThat(diagnostics.getFirst().message()).contains("session");
    }

    @Test
    void compile_agentQuery_missingUser_fails() {
        var content = Map.<String, Object>of("steps", List.of(
                Map.of("agent-query", Map.of("session", "analyst"))));
        CompilationOutcome outcome = compiler.compile("test", content);
        assertThat(outcome).isInstanceOf(CompilationOutcome.Failure.class);
        var diagnostics = ((CompilationOutcome.Failure) outcome).diagnostics();
        assertThat(diagnostics.getFirst().message()).contains("user");
    }

    @Test
    void compile_inlinePools_parsedIntoResult() {
        var content = Map.<String, Object>of(
                "pools", Map.of(
                        "voice", Map.of("agent-id", "voice-agent", "backend", "claude",
                                        "max-active", 10, "salience", 2,
                                        "clearing", Map.of("policy", "every-call")),
                        "email", Map.of("agent-id", "email-agent", "backend", "claude",
                                        "max-active", 5)),
                "steps", List.of(
                        Map.of("agent-session-open", Map.of("session", "s1", "pool", "voice")),
                        Map.of("agent-session-close", Map.of("session", "s1"))));
        CompilationOutcome outcome = compiler.compile("test", content);
        assertThat(outcome).isInstanceOf(CompilationOutcome.Success.class);

        var result = ((CompilationOutcome.Success) outcome).result();
        assertThat(result.poolDeclarations()).hasSize(2);
        var voicePool = result.poolDeclarations().stream()
                              .filter(p -> "voice".equals(p.name())).findFirst().orElseThrow();
        assertThat(voicePool.salience()).isEqualTo(2);
        assertThat(voicePool.clearing()).containsEntry("policy", "every-call");
        assertThat(voicePool.maxActive()).isEqualTo(10);
    }

    @Test
    void compile_noPools_emptyList() {
        var content = Map.<String, Object>of("steps", List.of(
                Map.of("agent-session-open", Map.of("session", "s1")),
                Map.of("agent-session-close", Map.of("session", "s1"))));
        CompilationOutcome outcome = compiler.compile("test", content);
        assertThat(outcome).isInstanceOf(CompilationOutcome.Success.class);

        var result = ((CompilationOutcome.Success) outcome).result();
        assertThat(result.poolDeclarations()).isEmpty();
    }

    @Test
    void compile_poolWithMaxPromptTokens() {
        var content = Map.<String, Object>of(
                "pools", Map.of("extractors", Map.of(
                        "agent-id", "extractor", "backend", "claude",
                        "max-active", 5, "max-prompt-tokens", 50000)),
                "steps", List.of(
                        Map.of("agent-session-open", Map.of("session", "e1", "pool", "extractors")),
                        Map.of("agent-session-close", Map.of("session", "e1"))));
        CompilationOutcome outcome = compiler.compile("test", content);
        assertThat(outcome).isInstanceOf(CompilationOutcome.Success.class);

        var result = ((CompilationOutcome.Success) outcome).result();
        var pool   = result.poolDeclarations().getFirst();
        assertThat(pool.maxPromptTokens()).isEqualTo(50000);
    }


}
