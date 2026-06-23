package io.casehub.platform.oidc;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.identity.MissingTenancyException;
import io.casehub.platform.api.identity.TenancyConstants;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.Set;

/**
 * OIDC-backed {@link CurrentPrincipal}, {@code @RequestScoped}.
 *
 * <p>{@code @Alternative @Priority(100)} — when this module is on the classpath,
 * this bean automatically displaces all non-alternative {@code CurrentPrincipal}
 * implementations: {@code MockCurrentPrincipal @DefaultBean} (platform),
 * {@code QhorusInboundCurrentPrincipal @ApplicationScoped} (qhorus),
 * {@code TenantScopedPrincipal @RequestScoped} (work). No {@code exclude-types}
 * configuration required.
 *
 * <p>Claim names are fixed platform contract: {@code tenancyId} (required String),
 * {@code crossTenantAdmin} (optional Boolean, defaults false).
 *
 * <p>When the identity is anonymous (unauthenticated request to a public endpoint),
 * returns sentinel values matching MockCurrentPrincipal defaults. The JWT is never
 * accessed in the anonymous path.
 */
@RequestScoped
@Alternative
@Priority(100)
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
            .orElseThrow(() -> new MissingTenancyException(identity.getPrincipal().getName()));
    }

    @Override
    public boolean isCrossTenantAdmin() {
        return !identity.isAnonymous() && jwt.<Boolean>claim("crossTenantAdmin").orElse(false);
    }
}
