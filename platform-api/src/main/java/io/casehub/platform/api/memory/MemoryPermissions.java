package io.casehub.platform.api.memory;

import io.casehub.platform.api.identity.CurrentPrincipal;

public final class MemoryPermissions {
    private MemoryPermissions() {}

    public static void assertTenant(String tenantId, CurrentPrincipal principal) {
        if (!principal.tenancyId().equals(tenantId))
            throw new SecurityException(
                "Tenant ID mismatch: claimed=" + tenantId
                + ", authenticated=" + principal.tenancyId());
    }
}
