package io.casehub.platform.mcp.server;

import io.casehub.platform.api.identity.CurrentPrincipal;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AnonymousCurrentPrincipalTest {

    @Test
    void providesAnonymousDefaults() {
        CurrentPrincipal principal = new AnonymousCurrentPrincipal();
        assertThat(principal.actorId()).isEqualTo("anonymous");
        assertThat(principal.groups()).isEmpty();
        assertThat(principal.tenancyId()).isNotBlank();
        assertThat(principal.isCrossTenantAdmin()).isFalse();
        assertThat(principal.isAuthenticated()).isFalse();
    }
}
