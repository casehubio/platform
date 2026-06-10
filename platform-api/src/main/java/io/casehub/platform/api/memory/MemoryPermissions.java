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

    /**
     * Async-safe form. When {@code requestContextActive=false} (e.g. in an
     * {@code @ObservesAsync} handler thread), trusts {@code tenantId} directly —
     * the caller is application code running after an authenticated event fire,
     * not an external actor. When {@code requestContextActive=true}, delegates
     * to {@link #assertTenant(String, CurrentPrincipal)}.
     *
     * <p>Canonical adapter implementation:
     * <pre>{@code
     * private boolean requestContextActive() {
     *     var c = Arc.container();
     *     return c == null || c.requestContext().isActive();
     * }
     * }</pre>
     * Returns {@code true} when (a) no CDI container (plain unit test — enforce),
     * or (b) CDI present and request context active. Returns {@code false} only
     * when CDI is present but request context is inactive — the {@code @ObservesAsync}
     * condition. All {@code @QuarkusTest} adapter test classes must be annotated
     * {@code @ActivateRequestContext} so this returns {@code true} during test execution.
     */
    public static void assertTenant(String tenantId, CurrentPrincipal principal,
                                    boolean requestContextActive) {
        if (requestContextActive) assertTenant(tenantId, principal);
    }
}
