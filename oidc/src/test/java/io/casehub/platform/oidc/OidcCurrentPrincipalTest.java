package io.casehub.platform.oidc;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.identity.MissingTenancyException;
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

    @BeforeEach
    void activateRequestContext() {
        Arc.container().requestContext().activate();
    }

    @AfterEach
    void terminateRequestContext() {
        Arc.container().requestContext().terminate();
    }

    // --- OIDC path: principal is JsonWebToken ---

    @Test
    void oidc_all_claims_present() {
        final JsonWebToken jwt = mock(JsonWebToken.class);
        when(identity.isAnonymous()).thenReturn(false);
        when(identity.getPrincipal()).thenReturn(jwt);
        when(jwt.getName()).thenReturn("alice");
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
    void oidc_crossTenantAdmin_absent_returns_false() {
        final JsonWebToken jwt = mock(JsonWebToken.class);
        when(identity.isAnonymous()).thenReturn(false);
        when(identity.getPrincipal()).thenReturn(jwt);
        doReturn(Optional.of("tenant-abc")).when(jwt).claim("tenancyId");
        doReturn(Optional.empty()).when(jwt).claim("crossTenantAdmin");
        when(identity.getAttribute("crossTenantAdmin")).thenReturn(null);

        assertFalse(principal.isCrossTenantAdmin());
    }

    @Test
    void oidc_tenancyId_absent_no_attribute_throws() {
        final JsonWebToken jwt = mock(JsonWebToken.class);
        when(identity.isAnonymous()).thenReturn(false);
        when(identity.getPrincipal()).thenReturn(jwt);
        when(jwt.getName()).thenReturn("alice");
        doReturn(Optional.empty()).when(jwt).claim("tenancyId");
        when(identity.getAttribute("tenancyId")).thenReturn(null);

        final var ex = assertThrows(MissingTenancyException.class, () -> principal.tenancyId());
        assertEquals("alice", ex.actorId());
    }

    @Test
    void oidc_claim_absent_falls_back_to_attribute() {
        final JsonWebToken jwt = mock(JsonWebToken.class);
        when(identity.isAnonymous()).thenReturn(false);
        when(identity.getPrincipal()).thenReturn(jwt);
        doReturn(Optional.empty()).when(jwt).claim("tenancyId");
        when(identity.getAttribute("tenancyId")).thenReturn("tenant-from-augmentor");

        assertEquals("tenant-from-augmentor", principal.tenancyId());
    }

    @Test
    void oidc_both_claim_and_attribute_present_claim_wins_tenancyId() {
        final JsonWebToken jwt = mock(JsonWebToken.class);
        when(identity.isAnonymous()).thenReturn(false);
        when(identity.getPrincipal()).thenReturn(jwt);
        doReturn(Optional.of("tenant-jwt")).when(jwt).claim("tenancyId");
        when(identity.getAttribute("tenancyId")).thenReturn("tenant-attr");

        assertEquals("tenant-jwt", principal.tenancyId());
    }

    @Test
    void oidc_both_claim_and_attribute_present_claim_wins_crossTenantAdmin() {
        final JsonWebToken jwt = mock(JsonWebToken.class);
        when(identity.isAnonymous()).thenReturn(false);
        when(identity.getPrincipal()).thenReturn(jwt);
        doReturn(Optional.of(true)).when(jwt).claim("crossTenantAdmin");
        when(identity.getAttribute("crossTenantAdmin")).thenReturn(false);

        assertTrue(principal.isCrossTenantAdmin());
    }

    // --- Non-OIDC path: principal is plain Principal ---

    @Test
    void non_oidc_reads_tenancyId_from_attribute() {
        final Principal p = () -> "bridge-service";
        when(identity.isAnonymous()).thenReturn(false);
        when(identity.getPrincipal()).thenReturn(p);
        when(identity.getAttribute("tenancyId")).thenReturn("tenant-xyz");

        assertEquals("tenant-xyz", principal.tenancyId());
    }

    @Test
    void non_oidc_reads_crossTenantAdmin_from_attribute() {
        final Principal p = () -> "bridge-service";
        when(identity.isAnonymous()).thenReturn(false);
        when(identity.getPrincipal()).thenReturn(p);
        when(identity.getAttribute("crossTenantAdmin")).thenReturn(true);

        assertTrue(principal.isCrossTenantAdmin());
    }

    @Test
    void non_oidc_no_attribute_throws() {
        final Principal p = () -> "unknown-service";
        when(identity.isAnonymous()).thenReturn(false);
        when(identity.getPrincipal()).thenReturn(p);
        when(identity.getAttribute("tenancyId")).thenReturn(null);

        final var ex = assertThrows(MissingTenancyException.class, () -> principal.tenancyId());
        assertEquals("unknown-service", ex.actorId());
    }

    @Test
    void non_oidc_no_crossTenantAdmin_attribute_returns_false() {
        final Principal p = () -> "bridge-service";
        when(identity.isAnonymous()).thenReturn(false);
        when(identity.getPrincipal()).thenReturn(p);
        when(identity.getAttribute("crossTenantAdmin")).thenReturn(null);

        assertFalse(principal.isCrossTenantAdmin());
    }

    @Test
    void non_oidc_tenancyId_wrong_type_throws() {
        final Principal p = () -> "bridge-service";
        when(identity.isAnonymous()).thenReturn(false);
        when(identity.getPrincipal()).thenReturn(p);
        when(identity.getAttribute("tenancyId")).thenReturn(42);

        assertThrows(IllegalStateException.class, () -> principal.tenancyId());
    }

    @Test
    void non_oidc_crossTenantAdmin_wrong_type_throws() {
        final Principal p = () -> "bridge-service";
        when(identity.isAnonymous()).thenReturn(false);
        when(identity.getPrincipal()).thenReturn(p);
        when(identity.getAttribute("crossTenantAdmin")).thenReturn("true");

        assertThrows(IllegalStateException.class, () -> principal.isCrossTenantAdmin());
    }

    // --- Anonymous path ---

    @Test
    void anonymous_returns_sentinels() {
        when(identity.isAnonymous()).thenReturn(true);

        assertEquals("anonymous", principal.actorId());
        assertTrue(principal.groups().isEmpty());
        assertEquals(TenancyConstants.DEFAULT_TENANT_ID, principal.tenancyId());
        assertFalse(principal.isCrossTenantAdmin());
        assertFalse(principal.isAuthenticated());
    }

    // --- ActorType integration ---

    @Test
    void isSystem_true_for_system_actorId() {
        final JsonWebToken jwt = mock(JsonWebToken.class);
        when(identity.isAnonymous()).thenReturn(false);
        when(identity.getPrincipal()).thenReturn(jwt);
        when(jwt.getName()).thenReturn("system");
        doReturn(Optional.of("tenant-abc")).when(jwt).claim("tenancyId");

        assertTrue(principal.isSystem());
    }
}
