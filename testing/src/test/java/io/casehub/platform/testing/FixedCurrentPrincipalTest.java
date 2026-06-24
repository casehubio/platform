package io.casehub.platform.testing;

import io.casehub.platform.api.identity.TenancyConstants;
import jakarta.annotation.Priority;
import jakarta.enterprise.inject.Alternative;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class FixedCurrentPrincipalTest {

    private FixedCurrentPrincipal principal;

    @BeforeEach
    void setUp() {
        principal = new FixedCurrentPrincipal();
    }

    @Test
    void is_alternative_with_priority_above_production_alternatives() {
        assertNotNull(FixedCurrentPrincipal.class.getAnnotation(Alternative.class));
        Priority priority = FixedCurrentPrincipal.class.getAnnotation(Priority.class);
        assertNotNull(priority, "must have @Priority");
        assertTrue(priority.value() > 100,
                "test fixture priority (" + priority.value() + ") must beat production @Alternative OidcCurrentPrincipal (100)");
    }

    @Test
    void default_actorId_is_system() {
        assertEquals("system", principal.actorId());
    }

    @Test
    void default_groups_are_empty() {
        assertTrue(principal.groups().isEmpty());
    }

    @Test
    void default_isSystem_is_true() {
        assertTrue(principal.isSystem());
    }

    @Test
    void default_isAuthenticated_is_true() {
        assertTrue(principal.isAuthenticated());
    }

    @Test
    void setActorId_changes_actorId() {
        principal.setActorId("alice");
        assertEquals("alice", principal.actorId());
    }

    @Test
    void setActorId_to_anonymous_makes_isAuthenticated_false() {
        principal.setActorId("anonymous");
        assertFalse(principal.isAuthenticated());
        assertFalse(principal.isSystem());
    }

    @Test
    void setGroups_replaces_groups() {
        principal.setGroups(Set.of("admin", "reviewer"));
        assertEquals(Set.of("admin", "reviewer"), principal.groups());
    }

    @Test
    void addGroup_appends_to_groups() {
        principal.addGroup("admin");
        principal.addGroup("reviewer");
        assertTrue(principal.groups().contains("admin"));
        assertTrue(principal.groups().contains("reviewer"));
    }

    @Test
    void roles_delegates_to_groups() {
        principal.setGroups(Set.of("admin"));
        assertEquals(principal.groups(), principal.roles());
    }

    @Test
    void hasGroup_returns_true_when_present() {
        principal.addGroup("admin");
        assertTrue(principal.hasGroup("admin"));
    }

    @Test
    void hasGroup_returns_false_when_absent() {
        assertFalse(principal.hasGroup("admin"));
    }

    @Test
    void default_tenancyId_is_default_tenant_id() {
        assertEquals(TenancyConstants.DEFAULT_TENANT_ID, principal.tenancyId());
    }

    @Test
    void default_isCrossTenantAdmin_is_false() {
        assertFalse(principal.isCrossTenantAdmin());
    }

    @Test
    void setTenancyId_changes_tenancyId() {
        principal.setTenancyId("acme-corp");
        assertEquals("acme-corp", principal.tenancyId());
    }

    @Test
    void setCrossTenantAdmin_changes_isCrossTenantAdmin() {
        principal.setCrossTenantAdmin(true);
        assertTrue(principal.isCrossTenantAdmin());
    }

    @Test
    void reset_restores_defaults() {
        principal.setActorId("alice");
        principal.addGroup("admin");
        principal.setTenancyId("acme-corp");
        principal.setCrossTenantAdmin(true);
        principal.reset();
        assertEquals("system", principal.actorId());
        assertTrue(principal.groups().isEmpty());
        assertEquals(TenancyConstants.DEFAULT_TENANT_ID, principal.tenancyId());
        assertFalse(principal.isCrossTenantAdmin());
        assertTrue(principal.isSystem());
    }

    @Test
    void groups_returns_unmodifiable_copy() {
        principal.addGroup("admin");
        assertThrows(UnsupportedOperationException.class,
                () -> principal.groups().add("hacker"));
    }
}
