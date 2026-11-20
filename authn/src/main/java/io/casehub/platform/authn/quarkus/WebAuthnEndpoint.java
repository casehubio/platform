package io.casehub.platform.authn.quarkus;

import io.casehub.platform.api.authn.AuthenticationContext;
import io.casehub.platform.api.authn.AuthenticationResult;
import io.casehub.platform.api.authn.AuthenticationRouter;
import io.casehub.platform.api.authn.ChallengeResponse;
import io.casehub.platform.api.authn.WebAuthnCredential;
import io.casehub.platform.api.authn.WebAuthnCredentialStore;
import io.casehub.platform.api.identity.PrincipalId;
import io.casehub.platform.api.mcp.ContextParam;
import io.casehub.platform.api.mcp.HttpMethod;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.platform.api.mcp.RestMethod;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
@McpDomain("webauthn")
public class WebAuthnEndpoint {

    @Inject
    WebAuthnCredentialStore credentialStore;

    @Inject
    AuthenticationRouter authenticationRouter;

    @PlatformQuery("List WebAuthn credentials for the current user")
    public List<WebAuthnCredential> listCredentials(
            @ContextParam("actorId") String actorId,
            @ContextParam("tenancyId") String tenancyId) {
        return credentialStore.findByActorId(actorId, tenancyId);
    }

    @PlatformMutation("Delete a WebAuthn credential")
    @RestMethod(HttpMethod.DELETE)
    public void deleteCredential(
            @PathParam String credentialId,
            @ContextParam("actorId") String actorId,
            @ContextParam("tenancyId") String tenancyId) {
        credentialStore.findByCredentialId(credentialId)
                       .filter(c -> c.actorId().equals(actorId) && c.tenancyId().equals(tenancyId))
                       .ifPresentOrElse(
                               c -> credentialStore.delete(credentialId),
                               () -> {
                                   throw new SecurityException("Credential not found or not owned by current user");
                               });
    }

    @PlatformMutation("Generate WebAuthn registration options")
    public ChallengeResponse registerOptions(
            String origin,
            @ContextParam("actorId") String actorId,
            @ContextParam("tenancyId") String tenancyId) {
        return authenticationRouter.initiate(new AuthenticationContext(
                "webauthn", tenancyId, origin,
                Optional.of(PrincipalId.human(actorId)),
                Map.of()));
    }

    @PlatformMutation("Complete WebAuthn registration")
    public AuthenticationResult registerComplete(
            String challengeId,
            Map<String, Object> attestation,
            @ContextParam("tenancyId") String tenancyId) {
        return authenticationRouter.verify("webauthn", challengeId, attestation);
    }
}
