package io.casehub.platform.credentials.quarkus;

import io.casehub.platform.api.credentials.CredentialPropertyKeys;
import io.casehub.platform.api.credentials.CredentialResolver;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@QuarkusTest
class QuarkusCredentialResolverTest {

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
    void known_ref_returns_credentials_via_passthrough() {
        Map<String, String> result = resolver.resolve("db-primary");
        assertThat(result)
                .containsEntry(CredentialPropertyKeys.USER, "admin")
                .containsEntry(CredentialPropertyKeys.PASSWORD, "s3cret")
                .hasSize(2);
    }

    @Test
    void unknown_ref_returns_empty_map_when_provider_returns_null() {
        assertThat(resolver.resolve("nonexistent")).isEmpty();
    }

    @Test
    void empty_map_from_provider_returns_empty_map() {
        assertThat(resolver.resolve("returns-empty")).isEmpty();
    }

    @Test
    void returned_map_is_immutable() {
        Map<String, String> result = resolver.resolve("db-primary");
        assertThatThrownBy(() -> result.put("extra", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
