package io.casehub.platform.authn.social;

import io.casehub.platform.api.authn.AuthenticationContext;
import io.casehub.platform.api.authn.ConsentRequest;
import io.casehub.platform.api.authn.IncrementalConsentHandler;
import io.casehub.platform.api.authn.ProviderUnavailableException;
import io.casehub.platform.api.identity.PrincipalId;

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
        var authProvider = providers.get(provider);
        if (authProvider == null) {
            throw new ProviderUnavailableException(provider, "No OAuth provider registered for: " + provider);
        }

        var context = new AuthenticationContext(
                provider, tenancyId, "incremental-consent",
                Optional.of(PrincipalId.human(actorId)),
                Map.of("additionalScopes", missingScopes));

        var challenge = (OAuthChallengeResponse) authProvider.initiate(context);

        return new ConsentRequest(
                challenge.authorizationUrl(),
                provider, missingScopes, challenge.challengeId());
    }
}
