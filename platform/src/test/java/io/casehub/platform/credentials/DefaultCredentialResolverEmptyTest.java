package io.casehub.platform.credentials;

import io.casehub.platform.api.credentials.CredentialPropertyKeys;
import io.casehub.platform.api.credentials.CredentialResolver;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests with default config — no credential properties set.
 */
@QuarkusTest
class DefaultCredentialResolverEmptyTest {

    @Inject CredentialResolver resolver;

    @Test
    void null_ref_returns_empty_map() {
        assertThat(resolver.resolve(null)).isEmpty();
    }

    @Test
    void blank_ref_returns_empty_map() {
        assertThat(resolver.resolve("   ")).isEmpty();
    }

    @Test
    void missing_ref_returns_empty_map() {
        assertThat(resolver.resolve("nonexistent-cred")).isEmpty();
    }
}
