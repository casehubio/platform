package io.casehub.platform.authn;

import io.casehub.platform.api.authn.JwtSigningKeyResolver;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NoOpJwtSigningKeyResolverTest {

    private final JwtSigningKeyResolver resolver = new NoOpJwtSigningKeyResolver();

    @Test
    void signingKeyPairThrowsUnsupported() {
        assertThatThrownBy(() -> resolver.signingKeyPair("tenant-1"))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("No JWT signing key configured");
    }

    @Test
    void keyIdThrowsUnsupported() {
        assertThatThrownBy(() -> resolver.keyId("tenant-1"))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("No JWT signing key configured");
    }

    @Test
    void publicKeysReturnsEmptyList() {
        assertThat(resolver.publicKeys()).isEmpty();
    }
}
