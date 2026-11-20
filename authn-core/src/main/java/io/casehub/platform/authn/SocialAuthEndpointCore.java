package io.casehub.platform.authn;

import io.casehub.platform.api.authn.AuthenticationContext;
import io.casehub.platform.api.authn.AuthenticationResult;
import io.casehub.platform.api.authn.AuthenticationRouter;
import io.casehub.platform.api.authn.ChallengeResponse;
import io.casehub.platform.api.mcp.ContextParam;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformMutation;

import java.util.Map;
import java.util.Optional;

@McpDomain("social-auth")
public class SocialAuthEndpointCore {

    private final AuthenticationRouter authenticationRouter;

    public SocialAuthEndpointCore(AuthenticationRouter authenticationRouter) {
        this.authenticationRouter = authenticationRouter;
    }

    @PlatformMutation("Initiate OAuth authorization flow for a social provider")
    public ChallengeResponse authorize(
            @PathParam String provider,
            String origin,
            @ContextParam("tenancyId") String tenancyId) {
        return authenticationRouter.initiate(new AuthenticationContext(
                provider, tenancyId, origin,
                Optional.empty(),
                Map.of()));
    }

    @PlatformMutation("Complete OAuth callback for a social provider")
    public AuthenticationResult callback(
            @PathParam String provider,
            String code,
            String state,
            @ContextParam("tenancyId") String tenancyId) {
        return authenticationRouter.verify(provider, state, Map.of("code", code));
    }
}
