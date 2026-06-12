package io.casehub.platform.api.identity;

/**
 * Sentinel values for multi-tenancy.
 *
 * <p>{@link #DEFAULT_TENANT_ID} is the single-tenant sentinel — returned by the
 * {@code @DefaultBean} mock and configurable via {@code casehub.tenancy.default-id}.
 * Single-tenant deployments need no configuration; this value is correct by default.
 *
 * <p>{@link #PLATFORM_TENANT_ID} is reserved for platform-level super-admin operations
 * that span all tenants. Not yet in active use.
 */
public final class TenancyConstants {

    /** Single-tenant sentinel. Configurable via {@code casehub.tenancy.default-id}. */
    public static final String DEFAULT_TENANT_ID = "278776f9-e1b0-46fb-9032-8bddebdcf9ce";

    /**
     * Reserved for two platform-level uses:
     * <ol>
     *   <li><b>Cross-tenant super-admin principals</b> — a principal whose
     *       {@link io.casehub.platform.api.identity.CurrentPrincipal#tenancyId()} returns
     *       this value has platform-wide access bypassing per-tenant filters.</li>
     *   <li><b>Platform-global endpoint registration</b> — an
     *       {@link io.casehub.platform.api.endpoints.EndpointDescriptor} with this
     *       {@code tenancyId} is visible to all tenants via
     *       {@link io.casehub.platform.api.endpoints.EndpointRegistry#resolve(io.casehub.platform.api.path.Path, String)}
     *       and
     *       {@link io.casehub.platform.api.endpoints.EndpointRegistry#discover(io.casehub.platform.api.endpoints.EndpointQuery)}.</li>
     * </ol>
     *
     * <p>Both uses share the same semantic root: owned by the platform, not any specific tenant.
     */
    public static final String PLATFORM_TENANT_ID = "platform";

    private TenancyConstants() {}
}
