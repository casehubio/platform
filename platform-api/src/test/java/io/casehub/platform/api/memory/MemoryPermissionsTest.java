package io.casehub.platform.api.memory;

import io.casehub.platform.api.identity.CurrentPrincipal;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class MemoryPermissionsTest {

    private static CurrentPrincipal principal(String tenancyId) {
        return new CurrentPrincipal() {
            @Override public String actorId()  { return "actor"; }
            @Override public Set<String> groups() { return Set.of(); }
            @Override public String tenancyId()   { return tenancyId; }
            @Override public boolean isCrossTenantAdmin() { return false; }
        };
    }

    @Test
    void matching_tenant_does_not_throw() {
        assertDoesNotThrow(() ->
            MemoryPermissions.assertTenant("tenant-a", principal("tenant-a")));
    }

    @Test
    void mismatched_tenant_throws_security_exception() {
        SecurityException ex = assertThrows(SecurityException.class, () ->
            MemoryPermissions.assertTenant("tenant-b", principal("tenant-a")));
        assertTrue(ex.getMessage().contains("tenant-b"));
        assertTrue(ex.getMessage().contains("tenant-a"));
    }
}
