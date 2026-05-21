package io.casehub.platform.oidc;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.identity.TenancyConstants;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.Set;

/**
 * @RequestScoped CurrentPrincipal backed by Quarkus SecurityIdentity and JsonWebToken.
 *
 * <p>Displaces MockCurrentPrincipal @DefaultBean automatically when this module is on
 * the classpath — no exclusion config required.
 *
 * <p>Claim names are fixed platform contract: {@code tenancyId} (required String),
 * {@code crossTenantAdmin} (optional Boolean, defaults false).
 *
 * <p>When the identity is anonymous (unauthenticated request to a public endpoint),
 * returns sentinel values matching MockCurrentPrincipal defaults. The JWT is never
 * accessed in the anonymous path.
 */
@RequestScoped
public class OidcCurrentPrincipal implements CurrentPrincipal {

    @Inject SecurityIdentity identity;
    @Inject JsonWebToken jwt;

    @Override
    public String actorId() {
        return identity.isAnonymous() ? "anonymous" : identity.getPrincipal().getName();
    }

    @Override
    public Set<String> groups() {
        return identity.isAnonymous() ? Set.of() : identity.getRoles();
    }

    @Override
    public String tenancyId() {
        if (identity.isAnonymous()) return TenancyConstants.DEFAULT_TENANT_ID;
        return jwt.<String>claim("tenancyId")
            .orElseThrow(() -> new IllegalStateException("JWT missing required claim: tenancyId"));
    }

    @Override
    public boolean isCrossTenantAdmin() {
        return !identity.isAnonymous() && jwt.<Boolean>claim("crossTenantAdmin").orElse(false);
    }
}
