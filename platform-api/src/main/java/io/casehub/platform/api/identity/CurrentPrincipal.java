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
 * <p>TODO: add {@code ActorType actorType()} once ActorType migrates from
 * casehub-ledger-api to casehub-platform-api (see casehubio/ledger migration issue).
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

    default boolean isSystem() { return "system".equals(actorId()); }

    default boolean isAuthenticated() { return !"anonymous".equals(actorId()); }
}
