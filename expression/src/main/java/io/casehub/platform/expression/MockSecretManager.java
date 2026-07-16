package io.casehub.platform.expression;

import io.casehub.platform.api.expression.SecretManager;
import io.casehub.platform.api.expression.SecretNotFoundException;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.ConfigProvider;

import java.util.HashMap;
import java.util.Map;

/**
 * {@code @DefaultBean} config-backed {@link SecretManager}.
 *
 * <p>Reads secrets from SmallRye Config by sweeping all properties with the prefix
 * {@code casehub.platform.secrets.{secretName}.}. Example: setting
 * {@code casehub.platform.secrets.openai.apiKey=sk-test} in {@code application.properties}
 * makes {@code $secret.openai.apiKey} available in JQ expressions.
 *
 * <h3>Kubernetes integration</h3>
 *
 * <p>Add {@code quarkus-kubernetes-config} to the consumer application classpath and
 * configure it to read a Kubernetes Secret whose keys use the
 * {@code casehub.platform.secrets.*} prefix:
 *
 * <pre>{@code
 * # application.properties (%prod profile)
 * %prod.quarkus.kubernetes-config.enabled=true
 * %prod.quarkus.kubernetes-config.secrets=casehub-secrets
 * %prod.quarkus.kubernetes-config.secrets.enabled=true
 * }</pre>
 *
 * <p>The Kubernetes Secret entries are injected into SmallRye Config and resolved
 * by this implementation automatically — no additional code or module required.
 *
 * <pre>{@code
 * apiVersion: v1
 * kind: Secret
 * metadata:
 *   name: casehub-secrets
 * stringData:
 *   casehub.platform.secrets.openai.apiKey: "sk-proj-..."
 *   casehub.platform.secrets.openai.organizationId: "org-..."
 * }</pre>
 *
 * <p>The extension is disabled by default, so dev and test environments are not
 * affected. See the
 * <a href="https://quarkus.io/guides/kubernetes-config">Quarkus Kubernetes Config guide</a>.
 *
 * <p>Displaced automatically when a non-default {@code @ApplicationScoped} {@link SecretManager}
 * is on the classpath (e.g. engine's {@code ConfigSecretManager}).
 */
@DefaultBean
@ApplicationScoped
public class MockSecretManager implements SecretManager {

    @Override
    public Map<String, Object> secret(String secretName) {
        String prefix = "casehub.platform.secrets." + secretName + ".";
        Map<String, Object> result = new HashMap<>();
        for (String name : ConfigProvider.getConfig().getPropertyNames()) {
            if (name.startsWith(prefix)) {
                ConfigProvider.getConfig().getOptionalValue(name, String.class)
                        .ifPresent(v -> put(result, name.substring(prefix.length()), v));
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
