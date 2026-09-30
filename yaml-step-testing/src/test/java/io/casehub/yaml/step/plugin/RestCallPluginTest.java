package io.casehub.yaml.step.plugin;

import io.casehub.yaml.plugin.api.MapServiceRegistry;
import io.casehub.yaml.plugin.api.Action;
import io.casehub.yaml.plugin.api.Result;
import io.casehub.yaml.step.testing.infrastructure.HttpMockInfrastructure;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RestCallPluginTest {

    private HttpMockInfrastructure infra;
    private String baseUrl;

    @BeforeEach
    void startServer() {
        infra = new HttpMockInfrastructure();
        infra.start();
        baseUrl = (String) infra.variableBindings().get("wiremock.url");

        infra.configure(List.of(
            Map.of(
                "request", Map.of("method", "GET", "path", "/ok"),
                "response", Map.of("status", 200,
                    "body", Map.of("status", "ok"))
            ),
            Map.of(
                "request", Map.of("method", "POST", "path", "/ok"),
                "response", Map.of("status", 200,
                    "body", Map.of("status", "ok"))
            ),
            Map.of(
                "request", Map.of("method", "GET", "path", "/error"),
                "response", Map.of("status", 500)
            )
        ));
    }

    @AfterEach
    void stopServer() {
        if (infra != null) infra.stop();
    }

    @Test
    void executesGetRequest() {
        var action = loadAction();
        var registry = new MapServiceRegistry();

        Result result = action.execute(
                Map.of("url", baseUrl + "/ok"), registry);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.output()).containsEntry("status-code", 200);
        assertThat(result.output().get("body").toString()).contains("ok");
    }

    @Test
    void executesPostRequest() {
        var action = loadAction();
        var registry = new MapServiceRegistry();

        Result result = action.execute(
                Map.of("url", baseUrl + "/ok",
                       "method", "POST"), registry);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.output()).containsEntry("status-code", 200);
    }

    @Test
    void reports5xxAsFailure() {
        var action = loadAction();
        var registry = new MapServiceRegistry();

        Result result = action.execute(
                Map.of("url", baseUrl + "/error"), registry);

        assertThat(result.isSuccess()).isFalse();
    }

    private Action loadAction() {
        try {
            Class<?> clazz = Class.forName(
                    "io.casehub.yaml.step.plugin.RestCallPluginAction");
            return (Action) clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new AssertionError("APT-generated RestCallPluginAction not found: " + e, e);
        }
    }
}
