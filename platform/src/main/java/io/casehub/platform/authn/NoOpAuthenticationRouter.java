package io.casehub.platform.authn;

import io.casehub.platform.api.authn.AuthenticationContext;
import io.casehub.platform.api.authn.AuthenticationResult;
import io.casehub.platform.api.authn.AuthenticationRouter;
import io.casehub.platform.api.authn.ChallengeResponse;
import io.casehub.platform.api.authn.ProviderUnavailableException;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Map;
import java.util.Set;

@DefaultBean
@ApplicationScoped
public class NoOpAuthenticationRouter implements AuthenticationRouter {

    @Override
    public ChallengeResponse initiate(AuthenticationContext context) {
        throw new ProviderUnavailableException(context.method(), "No authentication providers configured");
    }

    @Override
    public AuthenticationResult verify(String method, String challengeId, Map<String, Object> data) {
        throw new ProviderUnavailableException(method, "No authentication providers configured");
    }

    @Override
    public Set<String> availableMethods() {
        return Set.of();
    }
}
