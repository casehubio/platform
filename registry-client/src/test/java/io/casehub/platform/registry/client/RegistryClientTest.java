package io.casehub.platform.registry.client;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sun.net.httpserver.HttpServer;
import io.casehub.platform.api.registry.CascadeResult;
import io.casehub.platform.api.registry.HealthStatus;
import io.casehub.platform.api.registry.RegistryEntry;
import io.casehub.platform.api.registry.RegistryQuery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RegistryClientTest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

    private HttpServer server;
    private RegistryClient client;
    private String lastRequestMethod;
    private String lastRequestPath;
    private String lastRequestBody;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.start();
        int port = server.getAddress().getPort();
        client = new RegistryClient("http://localhost:" + port);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
        client.close();
    }

    private RegistryEntry entry(String id) {
        return new RegistryEntry(id, "service", "default", "tenant-1",
                Map.of(), Instant.now(), null, Duration.ofSeconds(30),
                HealthStatus.HEALTHY, null);
    }

    @Test
    void register_sendsPostWithEntry() throws Exception {
        var entry = entry("svc-1");
        server.createContext("/registry/entries", exchange -> {
            lastRequestMethod = exchange.getRequestMethod();
            lastRequestBody = new String(exchange.getRequestBody().readAllBytes());
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });

        client.register(entry);

        assertThat(lastRequestMethod).isEqualTo("POST");
        assertThat(lastRequestBody).contains("svc-1");
    }

    @Test
    void heartbeat_sendsPutToEntryId() throws Exception {
        server.createContext("/registry/entries/svc-1/heartbeat", exchange -> {
            lastRequestMethod = exchange.getRequestMethod();
            lastRequestPath = exchange.getRequestURI().getPath();
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });

        client.heartbeat("svc-1");

        assertThat(lastRequestMethod).isEqualTo("PUT");
        assertThat(lastRequestPath).isEqualTo("/registry/entries/svc-1/heartbeat");
    }

    @Test
    void deregister_returnsCascadeResult() throws Exception {
        var result = CascadeResult.simple(entry("svc-1"));
        var json = MAPPER.writeValueAsBytes(result);
        server.createContext("/registry/entries/svc-1", exchange -> {
            lastRequestMethod = exchange.getRequestMethod();
            lastRequestPath = exchange.getRequestURI().getPath();
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, json.length);
            exchange.getResponseBody().write(json);
            exchange.close();
        });

        var cascadeResult = client.deregister("svc-1");

        assertThat(lastRequestMethod).isEqualTo("DELETE");
        assertThat(lastRequestPath).isEqualTo("/registry/entries/svc-1");
        assertThat(cascadeResult.deregistered().id()).isEqualTo("svc-1");
        assertThat(cascadeResult.cascadeEffects()).isEmpty();
    }

    @Test
    void deregister_returnsEmptyResultOn204() throws Exception {
        server.createContext("/registry/entries/svc-1", exchange -> {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });

        var cascadeResult = client.deregister("svc-1");

        assertThat(cascadeResult.deregistered()).isNull();
        assertThat(cascadeResult.cascadeEffects()).isEmpty();
    }

    @Test
    void resolve_returnsEntry() throws Exception {
        var entry = entry("svc-1");
        var json = MAPPER.writeValueAsBytes(entry);
        server.createContext("/registry/entries/svc-1", exchange -> {
            lastRequestMethod = exchange.getRequestMethod();
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, json.length);
            exchange.getResponseBody().write(json);
            exchange.close();
        });

        var result = client.resolve("svc-1");

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo("svc-1");
        assertThat(lastRequestMethod).isEqualTo("GET");
    }

    @Test
    void resolve_returnsEmptyOn404() throws Exception {
        server.createContext("/registry/entries/nope", exchange -> {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });

        var result = client.resolve("nope");

        assertThat(result).isEmpty();
    }

    @Test
    void discover_returnsEntries() throws Exception {
        var entries = List.of(entry("svc-1"), entry("svc-2"));
        var json = MAPPER.writeValueAsBytes(entries);
        server.createContext("/registry/entries", exchange -> {
            lastRequestMethod = exchange.getRequestMethod();
            lastRequestPath = exchange.getRequestURI().toString();
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, json.length);
            exchange.getResponseBody().write(json);
            exchange.close();
        });

        var result = client.discover(new RegistryQuery("tenant-1", "service", null));

        assertThat(result).hasSize(2);
        assertThat(lastRequestMethod).isEqualTo("GET");
        assertThat(lastRequestPath).contains("tenancyId=tenant-1");
        assertThat(lastRequestPath).contains("type=service");
    }

    @Test
    void discover_urlEncodesQueryParams() throws Exception {
        var entries = List.of(entry("svc-1"));
        var json = MAPPER.writeValueAsBytes(entries);
        server.createContext("/registry/entries", exchange -> {
            lastRequestPath = exchange.getRequestURI().getRawQuery();
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, json.length);
            exchange.getResponseBody().write(json);
            exchange.close();
        });

        client.discover(new RegistryQuery("tenant with spaces", "type&evil=1", null));

        assertThat(lastRequestPath).contains("tenancyId=tenant+with+spaces");
        assertThat(lastRequestPath).contains("type=type%26evil%3D1");
    }

    @Test
    void register_throwsOnServerError() {
        server.createContext("/registry/entries", exchange -> {
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });

        assertThatThrownBy(() -> client.register(entry("svc-1")))
                .isInstanceOf(RegistryClientException.class)
                .hasMessageContaining("500");
    }

    @Test
    void heartbeat_throwsOnServerError() {
        server.createContext("/registry/entries/svc-1/heartbeat", exchange -> {
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });

        assertThatThrownBy(() -> client.heartbeat("svc-1"))
                .isInstanceOf(RegistryClientException.class);
    }
}
