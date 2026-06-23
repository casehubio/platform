package io.casehub.platform.api.identity;

import java.util.Set;

/**
 * Identity of the currently active principal.
 *
 * <p>Real implementations must be {@code @RequestScoped}, backed by the active security
 * context (e.g. Quarkus {@code SecurityIdentity}). Injecting a {@code @RequestScoped}
 * implementation into an {@code @ApplicationScoped} REST resource is safe — CDI client
 * proxies delegate to the correct contextual instance per request.
 *
 * <p>The {@code @DefaultBean} mock ({@code MockCurrentPrincipal}) is intentionally
 * {@code @ApplicationScoped}: no request context exists in dev/test mode, and the mock
 * reads from {@code @ConfigProperty}. {@code @DefaultBean} yields to any non-default
 * bean regardless of scope.
 *
 * <p>⚠ Do not access {@code CurrentPrincipal} inside reactive pipelines ({@code Uni}/
 * {@code Multi}) without {@code @ActivateRequestContext} — {@code @RequestScoped}
 * implementations will throw {@code ContextNotActiveException} when the request context
 * is not active on the executing thread.
 *
 */
public interface CurrentPrincipal {

    String actorId();

    Set<String> groups();

    /**
     * Groups serve as roles by convention — wires directly to {@code @RolesAllowed}
     * without an interface change. Override to separate roles from group membership
     * once RBAC matures.
     */
    default Set<String> roles() { return groups(); }

    /**
     * Override in directory-backed implementations — iterating the full group set on
     * every call is wasteful in production.
     */
    default boolean hasGroup(String group) { return groups().contains(group); }

    default ActorType actorType() { return ActorTypeResolver.resolve(actorId()); }

    default boolean isSystem() { return actorType() == ActorType.SYSTEM; }

    default boolean isAuthenticated() { return !"anonymous".equals(actorId()); }

    /**
     * The tenant this principal belongs to.
     *
     * <p>Single-tenant deployments return {@link TenancyConstants#DEFAULT_TENANT_ID}.
     * Real implementations derive this from the authenticated security context.
     *
     * <p>This value must never be sourced from user-supplied input — always derived
     * from the authenticated security context.
     *
     * @throws MissingTenancyException if the principal is authenticated but tenancy
     *         cannot be resolved from the security context
     */
    String tenancyId();

    /**
     * Whether this principal has cross-tenant admin access.
     *
     * <p>Must return {@code true} only for platform-level super-admin principals.
     * Intended to be checked once at CDI injection time in cross-tenant data access
     * classes — never at call sites. See protocol PP-20260520-e6a5f0.
     */
    boolean isCrossTenantAdmin();
}
