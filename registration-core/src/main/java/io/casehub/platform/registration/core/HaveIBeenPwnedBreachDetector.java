package io.casehub.platform.registration.core;

import io.casehub.platform.registration.BreachDetector;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

public class HaveIBeenPwnedBreachDetector implements BreachDetector {

    private static final String API_URL = "https://api.pwnedpasswords.com/range/";
    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    private final HttpClient httpClient;

    public HaveIBeenPwnedBreachDetector() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build();
    }

    HaveIBeenPwnedBreachDetector(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public boolean isBreached(String password) {
        String hash = sha1Hex(password).toUpperCase();
        String prefix = hash.substring(0, 5);
        String suffix = hash.substring(5);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL + prefix))
                .timeout(TIMEOUT)
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new RuntimeException("HIBP API returned " + response.statusCode());
            }
            return containsSuffix(response.body(), suffix);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("HIBP check interrupted", e);
        } catch (Exception e) {
            throw new RuntimeException("HIBP check failed", e);
        }
    }

    static boolean containsSuffix(String responseBody, String suffix) {
        for (String line : responseBody.split("\r\n")) {
            int colonIdx = line.indexOf(':');
            if (colonIdx > 0 && line.substring(0, colonIdx).equalsIgnoreCase(suffix)) {
                return true;
            }
        }
        return false;
    }

    static String sha1Hex(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 not available", e);
        }
    }
}
