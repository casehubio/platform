package io.casehub.platform.api.expression;

/**
 * Thrown when a requested secret does not exist.
 *
 * <p>Fail-fast: JQ expressions fail immediately if a referenced secret is missing.
 * Error messages must never expose secret values or sensitive metadata.
 */
public class SecretNotFoundException extends RuntimeException {

    private final String secretName;

    public SecretNotFoundException(String secretName) {
        super("Secret not found: " + secretName);
        this.secretName = secretName;
    }

    public String getSecretName() {
        return secretName;
    }
}
