package io.casehub.platform.authn.social;

import java.util.Set;

public interface OAuthConfig {
    String clientId();
    String clientSecret();
    String redirectUri();

    default Set<String> scopes() {
        return Set.of();
    }

    default long challengeTimeoutSeconds() {
        return 600;
    }
}
