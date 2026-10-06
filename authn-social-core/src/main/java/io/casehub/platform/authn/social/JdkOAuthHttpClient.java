package io.casehub.platform.authn.social;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;

public class JdkOAuthHttpClient implements OAuthHttpClient {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final HttpClient httpClient;

    public JdkOAuthHttpClient() {
        this(HttpClient.newHttpClient());
    }

    public JdkOAuthHttpClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public OAuthTokenResponse exchangeCode(String tokenEndpoint, Map<String, String> params) {
        String body = params.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)
                        + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));

        var request = HttpRequest.newBuilder()
                .uri(URI.create(tokenEndpoint))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        try {
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new OAuthException("Token exchange failed: HTTP " + response.statusCode()
                        + " — " + response.body());
            }
            Map<String, Object> json = JSON.readValue(response.body(), MAP_TYPE);
            return new OAuthTokenResponse(
                    (String) json.get("access_token"),
                    (String) json.get("refresh_token"),
                    (String) json.get("id_token"),
                    json.containsKey("expires_in") ? ((Number) json.get("expires_in")).longValue() : 0,
                    (String) json.get("token_type"),
                    (String) json.get("scope"));
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new OAuthException("Token exchange request failed: " + e.getMessage(), e);
        }
    }

    @Override
    public Map<String, Object> fetchUserInfo(String userInfoEndpoint, String accessToken) {
        var request = HttpRequest.newBuilder()
                .uri(URI.create(userInfoEndpoint))
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/json")
                .GET()
                .build();

        try {
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new OAuthException("User info request failed: HTTP " + response.statusCode());
            }
            return JSON.readValue(response.body(), MAP_TYPE);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new OAuthException("User info request failed: " + e.getMessage(), e);
        }
    }
}
