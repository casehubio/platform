package io.casehub.platform.authn;

import io.casehub.platform.api.authn.AuthenticationContext;
import io.casehub.platform.api.authn.AuthenticationRouter;
import io.casehub.platform.api.authn.ProviderUnavailableException;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NoOpAuthenticationRouterTest {

    private final AuthenticationRouter router = new NoOpAuthenticationRouter();

    @Test
    void initiateThrowsProviderUnavailable() {
        var context = new AuthenticationContext("webauthn", "tenant-1", "https://example.com", Optional.empty(), Map.of());
        assertThatThrownBy(() -> router.initiate(context))
                .isInstanceOf(ProviderUnavailableException.class)
                .hasMessageContaining("No authentication providers configured");
    }

    @Test
    void verifyThrowsProviderUnavailable() {
        assertThatThrownBy(() -> router.verify("webauthn", "challenge-1", Map.of()))
                .isInstanceOf(ProviderUnavailableException.class)
                .hasMessageContaining("No authentication providers configured");
    }

    @Test
    void availableMethodsReturnsEmpty() {
        assertThat(router.availableMethods()).isEmpty();
    }
}
