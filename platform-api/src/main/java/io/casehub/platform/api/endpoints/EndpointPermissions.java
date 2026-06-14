package io.casehub.platform.api.endpoints;

import io.casehub.platform.api.identity.CurrentPrincipal;

public final class EndpointPermissions {
    private EndpointPermissions() {}

    public static void assertTenant(String tenancyId, CurrentPrincipal principal) {
        if (!principal.tenancyId().equals(tenancyId))
            throw new SecurityException(
                "Tenant ID mismatch: claimed=" + tenancyId
                + ", authenticated=" + principal.tenancyId());
    }
}
