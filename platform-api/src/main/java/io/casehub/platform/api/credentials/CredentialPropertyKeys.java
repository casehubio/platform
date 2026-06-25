package io.casehub.platform.api.credentials;

/**
 * Reserved property keys for credential maps returned by {@link CredentialResolver#resolve}.
 *
 * <p>Standard keys use <b>kebab-case</b>. Values mirror Quarkus
 * {@code io.quarkus.credentials.CredentialsProvider} naming for bridge compatibility.
 *
 * <p>These keys are conventions, not enforced constraints. Implementations may
 * return additional custom keys. Consumers should use the constants for standard
 * credential extraction.
 */
public final class CredentialPropertyKeys {

    /** Username for basic auth, SASL, or compound credentials. */
    public static final String USER = "user";

    /** Password for basic auth, SASL, or compound credentials. */
    public static final String PASSWORD = "password";

    /** OAuth/JWT bearer token — the most common HTTP endpoint credential type. */
    public static final String BEARER_TOKEN = "bearer-token";

    /** API key for header-based authentication (e.g. X-Api-Key). */
    public static final String API_KEY = "api-key";

    /** ISO-8601 expiration timestamp for time-limited credentials. */
    public static final String EXPIRES_AT = "expires-at";

    private CredentialPropertyKeys() {}
}
