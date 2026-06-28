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

import java.util.Optional;
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
 * <p>Non-OIDC {@code HttpAuthenticationMechanism} implementations stamp these attributes
 * on the {@code SecurityIdentity} via {@code QuarkusSecurityIdentity.Builder.addAttribute()}:
 * <ul>
 *   <li>{@code "tenancyId"} (String, required) — tenant identifier</li>
 *   <li>{@code "crossTenantAdmin"} (Boolean, optional) — defaults to false</li>
 * </ul>
 * Attribute key names match {@link CurrentPrincipal} method names by convention.
 * Wrong-type attributes produce {@link IllegalStateException} with a diagnostic message.
 */
@RequestScoped
@Alternative
@Priority(100)
public class OidcCurrentPrincipal implements CurrentPrincipal {

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
            final Optional<String> claim = jwt.claim("tenancyId");
            if (claim.isPresent()) return claim.get();
        }

        final Object attr = identity.getAttribute("tenancyId");
        if (attr instanceof String s) return s;
        if (attr != null) throw new IllegalStateException(
            "SecurityIdentity attribute 'tenancyId' must be String, got: " + attr.getClass().getName());

        throw new MissingTenancyException(identity.getPrincipal().getName());
    }

    @Override
    public boolean isCrossTenantAdmin() {
        if (identity.isAnonymous()) return false;

        if (identity.getPrincipal() instanceof JsonWebToken jwt) {
            final Optional<Boolean> claim = jwt.claim("crossTenantAdmin");
            if (claim.isPresent()) return claim.get();
        }

        final Object attr = identity.getAttribute("crossTenantAdmin");
        if (attr instanceof Boolean b) return b;
        if (attr != null) throw new IllegalStateException(
            "SecurityIdentity attribute 'crossTenantAdmin' must be Boolean, got: " + attr.getClass().getName());

        return false;
    }
}
