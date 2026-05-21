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

    /** Reserved for platform-level cross-tenant super-admin principals. */
    public static final String PLATFORM_TENANT_ID = "platform";

    private TenancyConstants() {}
}
