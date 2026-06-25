package io.casehub.platform.credentials.quarkus;

import io.quarkus.credentials.CredentialsProvider;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.HashMap;
import java.util.Map;

@ApplicationScoped
public class MockCredentialsProvider implements CredentialsProvider {

    @Override
    public Map<String, String> getCredentials(final String credentialsProviderName) {
        return switch (credentialsProviderName) {
            case "db-primary" -> {
                final Map<String, String> creds = new HashMap<>();
                creds.put("user", "admin");
                creds.put("password", "s3cret");
                yield creds;
            }
            case "returns-empty" -> Map.of();
            case "returns-null" -> null;
            default -> null;
        };
    }
}
