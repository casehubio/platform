package io.casehub.platform.authn.social;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Base64;
import java.util.Map;

final class JwtUtils {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private JwtUtils() {}

    static Map<String, Object> parsePayload(String jwt) {
        String[] parts = jwt.split("\\.");
        if (parts.length < 2) {
            throw new OAuthException("Invalid JWT format: expected 3 parts, got " + parts.length);
        }
        try {
            byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
            return JSON.readValue(payload, MAP_TYPE);
        } catch (IOException e) {
            throw new OAuthException("Failed to parse JWT payload: " + e.getMessage(), e);
        }
    }
}
