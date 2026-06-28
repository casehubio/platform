package io.casehub.platform.oidc;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.identity.MissingTenancyException;
import io.casehub.platform.api.identity.SecurityIdentityAttributes;
import io.casehub.platform.api.identity.TenancyConstants;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.Set;

/**
 * SecurityIdentity-backed {@link CurrentPrincipal}, {@code @RequestScoped}.
 *
 * <p>{@code @Alternative @Priority(100)} — when this module is on the classpath,
 * this bean automatically displaces all non-alternative {@code CurrentPrincipal}
 * implementations: {@code MockCurrentPrincipal @DefaultBean} (platform),
 * {@code QhorusInboundCurrentPrincipal @ApplicationScoped} (qhorus),
 * {@code TenantScopedPrincipal @RequestScoped} (work). No {@code exclude-types}
 * configuration required.
 *
 * <h3>Resolution order for {@code tenancyId()} and {@code isCrossTenantAdmin()}</h3>
 * <ol>
 *   <li><b>Anonymous</b> — return sentinel ({@link TenancyConstants#DEFAULT_TENANT_ID} / false)</li>
 *   <li><b>JWT claim</b> — if the principal is a {@link JsonWebToken}, read the claim.
 *       Authoritative when present (cryptographically verified by the OIDC infrastructure).</li>
 *   <li><b>SecurityIdentity attribute</b> — fallback for non-OIDC
 *       {@code HttpAuthenticationMechanism} implementations or OIDC with
 *       {@code SecurityIdentityAugmentor}-provided attributes.</li>
 *   <li><b>Neither</b> — throw {@link MissingTenancyException} / return false</li>
 * </ol>
 *
 * <p>The {@code instanceof JsonWebToken} check on the principal is deliberately broader
 * than Quarkus's internal {@code instanceof OidcJwtCallerPrincipal} — it reads claims
 * from any JWT-bearing principal, including SmallRye JWT direct authentication
 * ({@code DefaultJWTCallerPrincipal}), not just OIDC-issued tokens.
 *
 * <h3>Attribute convention for non-OIDC mechanisms</h3>
 * <p>See {@link SecurityIdentityAttributes} for the reserved keys, their types,
 * and who sets them (OIDC IdP, {@code HttpAuthenticationMechanism}, or
 * {@code SecurityIdentityAugmentor}).
 *
 * <p>Blank or empty tenancyId values (from either JWT claims or attributes) are treated
 * as missing and fall through to the next resolution tier, consistent with the SPI
 * contract tested by {@code CurrentPrincipalSpiTest.tenancyId_returns_non_blank_string()}.
 */
@RequestScoped
@Alternative
@Priority(100)
public class SecurityIdentityCurrentPrincipal implements CurrentPrincipal {

    @Inject SecurityIdentity identity;

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

        if (identity.getPrincipal() instanceof JsonWebToken jwt) {
            final Object raw = jwt.getClaim(SecurityIdentityAttributes.TENANCY_ID);
            if (raw instanceof String s && !s.isBlank()) return s;
            if (raw != null && !(raw instanceof String)) throw new IllegalStateException(
                "JWT claim '" + SecurityIdentityAttributes.TENANCY_ID
                    + "' must be String, got: " + raw.getClass().getName());
        }

        final Object attr = identity.getAttribute(SecurityIdentityAttributes.TENANCY_ID);
        if (attr instanceof String s && !s.isBlank()) return s;
        if (attr != null && !(attr instanceof String)) throw new IllegalStateException(
            "SecurityIdentity attribute '" + SecurityIdentityAttributes.TENANCY_ID
                + "' must be String, got: " + attr.getClass().getName());

        throw new MissingTenancyException(identity.getPrincipal().getName(),
            "Checked JWT claims and SecurityIdentity attribute '"
                + SecurityIdentityAttributes.TENANCY_ID + "'");
    }

    @Override
    public boolean isCrossTenantAdmin() {
        if (identity.isAnonymous()) return false;

        if (identity.getPrincipal() instanceof JsonWebToken jwt) {
            final Object raw = jwt.getClaim(SecurityIdentityAttributes.CROSS_TENANT_ADMIN);
            if (raw instanceof Boolean b) return b;
            if (raw != null) throw new IllegalStateException(
                "JWT claim '" + SecurityIdentityAttributes.CROSS_TENANT_ADMIN
                    + "' must be Boolean, got: " + raw.getClass().getName());
        }

        final Object attr = identity.getAttribute(SecurityIdentityAttributes.CROSS_TENANT_ADMIN);
        if (attr instanceof Boolean b) return b;
        if (attr != null) throw new IllegalStateException(
            "SecurityIdentity attribute '" + SecurityIdentityAttributes.CROSS_TENANT_ADMIN
                + "' must be Boolean, got: " + attr.getClass().getName());

        return false;
    }
}
