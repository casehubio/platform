package io.casehub.platform.credentials;

import io.casehub.platform.api.credentials.CredentialPropertyKeys;
import io.casehub.platform.api.credentials.CredentialResolver;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests with credential config — simple and compound.
 */
@QuarkusTest
@TestProfile(DefaultCredentialResolverWithCredentialsTest.CredentialsProfile.class)
class DefaultCredentialResolverWithCredentialsTest {

    public static class CredentialsProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                "casehub.credentials.simple-token", "my-secret-token",
                "casehub.credentials.db-primary.user", "admin",
                "casehub.credentials.db-primary.password", "s3cret",
                "casehub.credentials.db-with-expiry.user", "svc",
                "casehub.credentials.db-with-expiry.password", "pw",
                "casehub.credentials.db-with-expiry.expires-at", "2026-07-01T00:00:00Z",
                "casehub.credentials.partial-compound.api-key", "key-only",
                "casehub.credentials.both-modes", "bare-value",
                "casehub.credentials.both-modes.user", "compound-user"
            );
        }
    }

    @Inject CredentialResolver resolver;

    @Test
    void simple_ref_returns_bearer_token() {
        Map<String, String> result = resolver.resolve("simple-token");
        assertThat(result).containsExactly(
                Map.entry(CredentialPropertyKeys.BEARER_TOKEN, "my-secret-token"));
    }

    @Test
    void compound_user_password_returns_both() {
        Map<String, String> result = resolver.resolve("db-primary");
        assertThat(result)
                .containsEntry(CredentialPropertyKeys.USER, "admin")
                .containsEntry(CredentialPropertyKeys.PASSWORD, "s3cret")
                .hasSize(2);
    }

    @Test
    void compound_with_expiry_returns_all_sub_keys() {
        Map<String, String> result = resolver.resolve("db-with-expiry");
        assertThat(result)
                .containsEntry(CredentialPropertyKeys.USER, "svc")
                .containsEntry(CredentialPropertyKeys.PASSWORD, "pw")
                .containsEntry(CredentialPropertyKeys.EXPIRES_AT, "2026-07-01T00:00:00Z")
                .hasSize(3);
    }

    @Test
    void partial_compound_returns_only_present_sub_keys() {
        Map<String, String> result = resolver.resolve("partial-compound");
        assertThat(result).containsExactly(
                Map.entry(CredentialPropertyKeys.API_KEY, "key-only"));
    }

    @Test
    void compound_takes_precedence_over_bare_key() {
        Map<String, String> result = resolver.resolve("both-modes");
        assertThat(result)
                .containsEntry(CredentialPropertyKeys.USER, "compound-user")
                .doesNotContainKey(CredentialPropertyKeys.BEARER_TOKEN)
                .hasSize(1);
    }
}
