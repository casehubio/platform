package io.casehub.platform.api.identity;

/**
 * Reserved keys used as both <b>JWT claim names</b> and <b>SecurityIdentity attribute keys</b>
 * by {@link CurrentPrincipal} implementations.
 *
 * <p>The resolution order (JWT claim first, attribute fallback) is documented in
 * {@code SecurityIdentityCurrentPrincipal} (casehub-platform-oidc module).
 *
 * <h3>Who sets these values</h3>
 * <ul>
 *   <li><b>OIDC IdP</b> — custom JWT claims configured on the identity provider</li>
 *   <li><b>Non-OIDC {@code HttpAuthenticationMechanism}</b> — stamps attributes directly
 *       via {@code QuarkusSecurityIdentity.Builder.addAttribute()}</li>
 *   <li><b>{@code SecurityIdentityAugmentor}</b> — adds attributes to an existing OIDC
 *       identity when the IdP cannot issue custom claims</li>
 * </ul>
 */
public final class SecurityIdentityAttributes {

    /**
     * Tenant identifier. Must be a non-blank {@code String}.
     *
     * <p>JWT claim: {@code jwt.claim("tenancyId")}
     * <br>Attribute: {@code identity.getAttribute("tenancyId")}
     */
    public static final String TENANCY_ID = "tenancyId";

    /**
     * Cross-tenant admin flag. Must be a {@code Boolean}. Defaults to {@code false} when absent.
     *
     * <p>JWT claim: {@code jwt.claim("crossTenantAdmin")}
     * <br>Attribute: {@code identity.getAttribute("crossTenantAdmin")}
     */
    public static final String CROSS_TENANT_ADMIN = "crossTenantAdmin";

    private SecurityIdentityAttributes() {}
}
