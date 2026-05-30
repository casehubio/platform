package io.casehub.platform.scim;

import io.quarkus.oidc.client.NamedOidcClient;
import io.quarkus.oidc.client.OidcClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
@ApplicationScoped
public class ScimAuthFilter implements ClientRequestFilter {

    @Inject ScimConfig config;
    @Inject @NamedOidcClient("scim") Instance<OidcClient> oidcClient;

    @Override
    public void filter(ClientRequestContext ctx) {
        String token = config.token().orElseGet(this::fetchOidcToken);
        ctx.getHeaders().putSingle("Authorization", "Bearer " + token);
    }

    private String fetchOidcToken() {
        // If quarkus.oidc-client.scim.* is not configured, Quarkus fails at startup
        // with a deployment error — no runtime check needed here.
        return oidcClient.get().getTokens().await().indefinitely().getAccessToken();
    }
}
