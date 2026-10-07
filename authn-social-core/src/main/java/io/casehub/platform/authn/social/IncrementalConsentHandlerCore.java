package io.casehub.platform.authn.social;

import io.casehub.platform.api.authn.AuthenticationContext;
import io.casehub.platform.api.authn.ConsentRequest;
import io.casehub.platform.api.authn.IncrementalConsentHandler;
import io.casehub.platform.api.authn.ProviderUnavailableException;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class IncrementalConsentHandlerCore implements IncrementalConsentHandler {

    private final Map<String, AbstractOAuthAuthenticationProvider> providers;

    public IncrementalConsentHandlerCore(
            Map<String, AbstractOAuthAuthenticationProvider> providers) {
        this.providers = Map.copyOf(providers);
    }

    @Override
    public ConsentRequest requestAdditionalScopes(
            String actorId, String provider, String tenancyId,
            Set<String> missingScopes) {
        java.util.Objects.requireNonNull(missingScopes, "missingScopes");
        if (missingScopes.isEmpty()) {
            throw new IllegalArgumentException("missingScopes must not be empty");
        }
        var authProvider = providers.get(provider);
        if (authProvider == null) {
            throw new ProviderUnavailableException(provider, "No OAuth provider registered for: " + provider);
        }

        var context = new AuthenticationContext(
                provider, tenancyId, "incremental-consent",
                Optional.of(io.casehub.platform.api.identity.PrincipalId.human(actorId)),
                Map.of("additionalScopes", missingScopes));

        var challenge = authProvider.initiate(context);
        if (!(challenge instanceof OAuthChallengeResponse oauthChallenge)) {
            throw new IllegalStateException(
                    "Provider " + provider + " returned " + challenge.getClass().getSimpleName()
                    + " instead of OAuthChallengeResponse");
        }

        return new ConsentRequest(
                oauthChallenge.authorizationUrl(),
                provider, missingScopes, oauthChallenge.challengeId());
    }
}
