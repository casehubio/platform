package io.casehub.platform.registry.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.casehub.platform.api.registry.CascadeResult;
import io.casehub.platform.api.registry.RegistryEntry;
import io.casehub.platform.api.registry.RegistryQuery;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class RegistryClient implements AutoCloseable {

    private static final TypeReference<List<RegistryEntry>> ENTRY_LIST_TYPE = new TypeReference<>() {};
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final String baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final boolean ownsClient;

    public RegistryClient(final String baseUrl) {
        this(baseUrl,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
                new ObjectMapper()
                        .registerModule(new JavaTimeModule())
                        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false),
                true);
    }

    public RegistryClient(final String baseUrl, final HttpClient httpClient, final ObjectMapper mapper) {
        this(baseUrl, httpClient, mapper, false);
    }

    private RegistryClient(final String baseUrl, final HttpClient httpClient,
                           final ObjectMapper mapper, final boolean ownsClient) {
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl").replaceAll("/+$", "");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.ownsClient = ownsClient;
    }

    public void register(final RegistryEntry entry) {
        try {
            var body = mapper.writeValueAsBytes(entry);
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/registry/entries"))
                    .header("Content-Type", "application/json")
                    .timeout(REQUEST_TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            requireSuccess(response, "register");
        } catch (RegistryClientException e) {
            throw e;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new RegistryClientException("Failed to register entry: " + e.getMessage(), e);
        }
    }

    public void heartbeat(final String id) {
        try {
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/registry/entries/" + id + "/heartbeat"))
                    .timeout(REQUEST_TIMEOUT)
                    .PUT(HttpRequest.BodyPublishers.noBody())
                    .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            requireSuccess(response, "heartbeat");
        } catch (RegistryClientException e) {
            throw e;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new RegistryClientException("Failed to heartbeat: " + e.getMessage(), e);
        }
    }

    public CascadeResult deregister(final String id) {
        try {
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/registry/entries/" + id))
                    .timeout(REQUEST_TIMEOUT)
                    .DELETE()
                    .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            requireSuccess(response, "deregister");
            if (response.statusCode() == 204 || response.body().length == 0) {
                return CascadeResult.empty();
            }
            return mapper.readValue(response.body(), CascadeResult.class);
        } catch (RegistryClientException e) {
            throw e;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new RegistryClientException("Failed to deregister: " + e.getMessage(), e);
        }
    }

    public Optional<RegistryEntry> resolve(final String id) {
        try {
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/registry/entries/" + id))
                    .timeout(REQUEST_TIMEOUT)
                    .GET()
                    .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() == 404) return Optional.empty();
            requireSuccess(response, "resolve");
            return Optional.of(mapper.readValue(response.body(), RegistryEntry.class));
        } catch (RegistryClientException e) {
            throw e;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new RegistryClientException("Failed to resolve: " + e.getMessage(), e);
        }
    }

    public List<RegistryEntry> discover(final RegistryQuery query) {
        try {
            var uri = new StringBuilder(baseUrl)
                    .append("/registry/entries?tenancyId=").append(encode(query.tenancyId()));
            if (query.type() != null) uri.append("&type=").append(encode(query.type()));
            if (query.namespace() != null) uri.append("&namespace=").append(encode(query.namespace()));

            var request = HttpRequest.newBuilder()
                    .uri(URI.create(uri.toString()))
                    .timeout(REQUEST_TIMEOUT)
                    .GET()
                    .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            requireSuccess(response, "discover");
            return mapper.readValue(response.body(), ENTRY_LIST_TYPE);
        } catch (RegistryClientException e) {
            throw e;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new RegistryClientException("Failed to discover: " + e.getMessage(), e);
        }
    }

    @Override
    public void close() {
        if (ownsClient) {
            httpClient.close();
        }
    }

    private static String encode(final String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private void requireSuccess(final HttpResponse<?> response, final String operation) {
        int status = response.statusCode();
        if (status >= 400) {
            throw new RegistryClientException(
                    operation + " failed with HTTP " + status, status);
        }
    }
}
