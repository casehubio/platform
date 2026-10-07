package io.casehub.platform.authn.quarkus;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

@ConfigMapping(prefix = "casehub.authn")
public interface AuthnConfig {

    @WithDefault("900")
    long accessTokenTtlSeconds();

    @WithDefault("2592000")
    long refreshTokenTtlSeconds();

    Optional<WebAuthn> webauthn();

    Map<String, SocialProvider> social();

    interface WebAuthn {
        String rpId();
        String rpName();
        Set<String> allowedOrigins();

        @WithDefault("300")
        long challengeTimeoutSeconds();

        @WithDefault("32")
        int challengeLength();
    }

    interface SocialProvider {
        String clientId();
        String clientSecret();
        String redirectUri();
        Optional<Set<String>> scopes();
        Optional<String> teamId();
        Optional<String> keyId();
        Optional<String> privateKey();
    }
}
