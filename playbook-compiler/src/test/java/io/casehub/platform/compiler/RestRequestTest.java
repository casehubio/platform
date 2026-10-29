package io.casehub.platform.compiler;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RestRequestTest {

    private final PlaybookCompiler compiler = new PlaybookCompiler();

    @Test
    void restRequest_compilesToCallHttp() {
        var content = Map.<String, Object>of("steps", List.of(
                Map.of("rest-request", Map.of("method", "GET", "url", "https://api.example.com/users"))));

        var outcome = compiler.compile("test", content);
        assertThat(outcome).isInstanceOf(CompilationOutcome.Success.class);

        var result = ((CompilationOutcome.Success) outcome).result();
        var task = result.workflow().getDo().getFirst().getTask();
        assertThat(task.getCallTask()).isNotNull();
        assertThat(task.getCallTask().getCallHTTP()).isNotNull();
        assertThat(task.getCallTask().getCallHTTP().getWith().getMethod()).isEqualTo("GET");
    }

    @Test
    void restRequest_post_includesBody() {
        var content = Map.<String, Object>of("steps", List.of(
                Map.of("rest-request", Map.of(
                        "method", "POST",
                        "url", "https://api.example.com/orders",
                        "body", Map.of("item", "widget", "qty", 5)))));

        var outcome = compiler.compile("test", content);
        assertThat(outcome).isInstanceOf(CompilationOutcome.Success.class);

        var result = ((CompilationOutcome.Success) outcome).result();
        var httpArgs = result.workflow().getDo().getFirst().getTask()
                .getCallTask().getCallHTTP().getWith();
        assertThat(httpArgs.getMethod()).isEqualTo("POST");
        assertThat(httpArgs.getBody()).isNotNull();
    }

    @Test
    void integrationSmokeTest_fullPlaybook() {
        var tryStep = new LinkedHashMap<String, Object>();
        tryStep.put("try", List.of(
                Map.of("rest-request", Map.of("method", "POST", "url", "https://api.example.com/orders",
                        "body", Map.of("item", "widget")))));
        tryStep.put("catch", List.of(Map.of("log", Map.of("message", "Order failed"))));

        var content = new LinkedHashMap<String, Object>();
        content.put("playbook", "order-flow");
        content.put("steps", List.of(
                Map.of("log", Map.of("message", "Starting")),
                Map.of("rest-request", Map.of("method", "GET", "url", "https://api.example.com/users")),
                tryStep,
                Map.of("delay", "500ms"),
                Map.of("log", Map.of("message", "Done"))));

        var outcome = compiler.compile("order-flow", content);
        assertThat(outcome).isInstanceOf(CompilationOutcome.Success.class);

        var result = ((CompilationOutcome.Success) outcome).result();
        assertThat(result.workflow().getDo()).hasSize(5);
        assertThat(result.sourceMap().entries()).isNotEmpty();
        assertThat(result.coordinationSpec().isEmpty()).isTrue();
    }


    @Test
    void restRequest_expressionVariableInUrl() {
        var content = Map.<String, Object>of("steps", List.of(
                Map.of("rest-request", Map.of(
                        "method", "GET",
                        "url", "https://api.example.com/contractors/${input.contractorName}"))));

        var outcome = compiler.compile("test", content);
        assertThat(outcome).isInstanceOf(CompilationOutcome.Success.class);

        var result = ((CompilationOutcome.Success) outcome).result();
        var task = result.workflow().getDo().getFirst().getTask();
        var httpArgs = task.getCallTask().getCallHTTP().getWith();
        assertThat(httpArgs.getMethod()).isEqualTo("GET");
        assertThat(httpArgs.getEndpoint().getUriTemplate().getLiteralUriTemplate())
                .isEqualTo("https://api.example.com/contractors/${input.contractorName}");
    }

    @Test
    void restRequest_pureExpressionUrl() {
        var content = Map.<String, Object>of("steps", List.of(
                Map.of("rest-request", Map.of(
                        "method", "POST",
                        "url", "${config.apiBaseUrl}",
                        "body", Map.of("key", "value")))));

        var outcome = compiler.compile("test", content);
        assertThat(outcome).isInstanceOf(CompilationOutcome.Success.class);

        var result = ((CompilationOutcome.Success) outcome).result();
        var task = result.workflow().getDo().getFirst().getTask();
        var httpArgs = task.getCallTask().getCallHTTP().getWith();
        assertThat(httpArgs.getEndpoint().getRuntimeExpression())
                .isEqualTo("${config.apiBaseUrl}");
    }


    @Test
    void decoratedRestRequest_retryAndTimeout() {
        var step = new LinkedHashMap<String, Object>();
        step.put("rest-request", Map.of("method", "POST", "url", "https://api.example.com/orders"));
        step.put("retry", 3);
        step.put("timeout", "5s");

        var content = Map.<String, Object>of("steps", List.of(step));
        var outcome = compiler.compile("test", content);
        assertThat(outcome).isInstanceOf(CompilationOutcome.Success.class);

        var result = ((CompilationOutcome.Success) outcome).result();
        assertThat(result.workflow().getDo()).hasSize(1);
    }
}
