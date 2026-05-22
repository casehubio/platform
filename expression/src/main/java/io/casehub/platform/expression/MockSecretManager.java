package io.casehub.platform.expression;

import io.casehub.platform.api.expression.SecretManager;
import io.casehub.platform.api.expression.SecretNotFoundException;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * {@code @DefaultBean} mock for {@link SecretManager}.
 *
 * <p>Reads secrets from SmallRye Config using the prefix {@code casehub.platform.secrets.*}.
 * Example: {@code casehub.platform.secrets.openai.apiKey=sk-test} makes
 * {@code $secret.openai.apiKey} available in JQ expressions during dev and test.
 *
 * <p>Displaced automatically when a non-default {@code @ApplicationScoped} {@link SecretManager}
 * is on the classpath (e.g. engine's {@code ConfigSecretManager}).
 */
@DefaultBean
@ApplicationScoped
public class MockSecretManager implements SecretManager {

    @ConfigProperty(name = "casehub.platform.secrets")
    Optional<Map<String, String>> secretsConfig;

    @Override
    public Map<String, Object> secret(String secretName) {
        Map<String, String> all = secretsConfig.orElse(Map.of());
        String prefix = secretName + ".";
        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<String, String> entry : all.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                put(result, entry.getKey().substring(prefix.length()), entry.getValue());
            }
        }
        if (result.isEmpty()) throw new SecretNotFoundException(secretName);
        return result;
    }

    @SuppressWarnings("unchecked")
    private static void put(Map<String, Object> map, String key, String value) {
        int dot = key.indexOf('.');
        if (dot == -1) { map.put(key, value); return; }
        String head = key.substring(0, dot);
        String tail = key.substring(dot + 1);
        Map<String, Object> nested = (Map<String, Object>)
                map.computeIfAbsent(head, k -> new HashMap<>());
        put(nested, tail, value);
    }
}
