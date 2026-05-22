package io.casehub.platform.api.expression;

import java.util.Map;

/**
 * Resolves secrets from various backends (system properties, K8s Secrets, Vault, etc.).
 *
 * <p>Secrets are accessible from JQ expressions via {@code $secret.{secretName}.{property}} syntax.
 *
 * <p>Example: {@code casehub.platform.secrets.openai.apiKey=sk-test} in application.properties
 * makes {@code $secret.openai.apiKey} available in JQ expressions.
 */
public interface SecretManager {

    /**
     * Resolve a secret by name.
     *
     * @param secretName secret identifier (e.g., "openai", "database")
     * @return map of secret properties (e.g., {apiKey: "sk-...", orgId: "..."})
     * @throws SecretNotFoundException if the secret does not exist
     */
    Map<String, Object> secret(String secretName);
}
