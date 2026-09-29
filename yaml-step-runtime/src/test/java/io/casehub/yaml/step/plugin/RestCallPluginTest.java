package io.casehub.yaml.step.plugin;

import io.casehub.yaml.plugin.api.MapServiceRegistry;
import io.casehub.yaml.plugin.api.Action;
import io.casehub.yaml.plugin.api.Result;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RestCallPluginTest {

    private HttpServer server;
    private int port;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        port = server.getAddress().getPort();
        server.createContext("/ok", exchange -> {
            byte[] body = "{\"status\":\"ok\"}".getBytes();
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/error", exchange -> {
            byte[] body = "Internal Server Error".getBytes();
            exchange.sendResponseHeaders(500, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void executesGetRequest() {
        var action = loadAction();
        var registry = new MapServiceRegistry();

        Result result = action.execute(
                Map.of("url", "http://localhost:" + port + "/ok"), registry);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.output()).containsEntry("status-code", 200);
        assertThat(result.output().get("body").toString()).contains("ok");
    }

    @Test
    void executesPostRequest() {
        var action = loadAction();
        var registry = new MapServiceRegistry();

        Result result = action.execute(
                Map.of("url", "http://localhost:" + port + "/ok",
                       "method", "POST"), registry);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.output()).containsEntry("status-code", 200);
    }

    @Test
    void reports5xxAsFailure() {
        var action = loadAction();
        var registry = new MapServiceRegistry();

        Result result = action.execute(
                Map.of("url", "http://localhost:" + port + "/error"), registry);

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
