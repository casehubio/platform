package io.casehub.platform.authn;

import io.casehub.platform.api.authn.UserResolver;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NoOpUserResolverTest {

    private final UserResolver resolver = new NoOpUserResolver();

    @Test
    void resolveByEmailReturnsEmpty() {
        assertThat(resolver.resolveByEmail("user@example.com", "tenant-1")).isEmpty();
    }
}
