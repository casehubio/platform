package io.casehub.platform.oidc;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.identity.TenancyConstants;
import io.quarkus.arc.Arc;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.Principal;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@QuarkusTest
class OidcCurrentPrincipalTest {

    @Inject CurrentPrincipal principal;

    @InjectMock SecurityIdentity identity;
    @InjectMock JsonWebToken jwt;

    @BeforeEach
    void activateRequestContext() {
        Arc.container().requestContext().activate();
    }

    @AfterEach
    void terminateRequestContext() {
        Arc.container().requestContext().terminate();
    }

    @Test
    void authenticated_all_claims_present() {
        Principal p = () -> "alice";
        when(identity.isAnonymous()).thenReturn(false);
        when(identity.getPrincipal()).thenReturn(p);
        when(identity.getRoles()).thenReturn(Set.of("admin", "reviewer"));
        doReturn(Optional.of("tenant-abc")).when(jwt).claim("tenancyId");
        doReturn(Optional.of(true)).when(jwt).claim("crossTenantAdmin");

        assertEquals("alice", principal.actorId());
        assertEquals(Set.of("admin", "reviewer"), principal.groups());
        assertEquals("tenant-abc", principal.tenancyId());
        assertTrue(principal.isCrossTenantAdmin());
        assertTrue(principal.isAuthenticated());
        assertFalse(principal.isSystem());
    }

    @Test
    void authenticated_crossTenantAdmin_absent_returns_false() {
        when(identity.isAnonymous()).thenReturn(false);
        doReturn(Optional.of("tenant-abc")).when(jwt).claim("tenancyId");
        doReturn(Optional.empty()).when(jwt).claim("crossTenantAdmin");

        assertFalse(principal.isCrossTenantAdmin());
    }

    @Test
    void authenticated_tenancyId_absent_throws() {
        when(identity.isAnonymous()).thenReturn(false);
        doReturn(Optional.empty()).when(jwt).claim("tenancyId");

        assertThrows(IllegalStateException.class, () -> principal.tenancyId());
    }

    @Test
    void anonymous_identity_returns_sentinels() {
        when(identity.isAnonymous()).thenReturn(true);

        assertEquals("anonymous", principal.actorId());
        assertTrue(principal.groups().isEmpty());
        assertEquals(TenancyConstants.DEFAULT_TENANT_ID, principal.tenancyId());
        assertFalse(principal.isCrossTenantAdmin());
        assertFalse(principal.isAuthenticated());
        verifyNoInteractions(jwt);
    }

    @Test
    void isSystem_true_for_system_actorId() {
        when(identity.isAnonymous()).thenReturn(false);
        when(identity.getPrincipal()).thenReturn(() -> "system");
        doReturn(Optional.of("tenant-abc")).when(jwt).claim("tenancyId");

        assertTrue(principal.isSystem());
    }
}
