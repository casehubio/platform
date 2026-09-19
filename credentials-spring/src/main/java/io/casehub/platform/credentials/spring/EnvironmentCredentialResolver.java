package io.casehub.platform.credentials.spring;

import io.casehub.platform.api.credentials.CredentialPropertyKeys;
import io.casehub.platform.api.credentials.CredentialResolver;
import org.springframework.core.env.Environment;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class EnvironmentCredentialResolver implements CredentialResolver {

    private static final List<String> STANDARD_KEYS = List.of(
            CredentialPropertyKeys.USER,
            CredentialPropertyKeys.PASSWORD,
            CredentialPropertyKeys.BEARER_TOKEN,
            CredentialPropertyKeys.API_KEY,
            CredentialPropertyKeys.EXPIRES_AT,
            CredentialPropertyKeys.SIGNING_SECRET
    );

    private final Environment environment;

    public EnvironmentCredentialResolver(Environment environment) {
        this.environment = environment;
    }

    @Override
    public Map<String, String> resolve(String credentialRef) {
        if (credentialRef == null || credentialRef.isBlank()) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (String key : STANDARD_KEYS) {
            String value = environment.getProperty(credentialRef + "." + key);
            if (value != null) {
                result.put(key, value);
            }
        }
        return result.isEmpty() ? Map.of() : Map.copyOf(result);
    }
}
