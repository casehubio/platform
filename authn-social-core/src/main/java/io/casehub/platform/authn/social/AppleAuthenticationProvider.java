package io.casehub.platform.authn.social;

import io.casehub.platform.api.authn.AuthenticationContext;
import io.casehub.platform.api.authn.AuthenticationEventListener;
import io.casehub.platform.api.authn.IdentityBindingStore;
import io.casehub.platform.api.authn.OAuthTokenStore;
import io.casehub.platform.api.authn.UserResolver;

import java.util.Map;
import java.util.Set;

public class AppleAuthenticationProvider extends AbstractOAuthAuthenticationProvider {

    private static final String METHOD = "apple";
    private static final String AUTHORIZATION_ENDPOINT = "https://appleid.apple.com/auth/authorize";
    private static final String TOKEN_ENDPOINT = "https://appleid.apple.com/auth/token";
    private static final Set<String> DEFAULT_SCOPES = Set.of("openid", "email", "name");

    private final AppleConfig appleConfig;

    public AppleAuthenticationProvider(AppleConfig config,
                                       OAuthHttpClient httpClient,
                                       OAuthTokenStore tokenStore,
                                       IdentityBindingStore bindingStore,
                                       UserResolver userResolver,
                                       AuthenticationEventListener eventListener) {
        super(config, httpClient, tokenStore, bindingStore, userResolver, eventListener);
        this.appleConfig = config;
    }

    @Override
    public String method() {
        return METHOD;
    }

    @Override
    protected String authorizationEndpoint() {
        return AUTHORIZATION_ENDPOINT;
    }

    @Override
    protected String tokenEndpoint() {
        return TOKEN_ENDPOINT;
    }

    @Override
    protected Map<String, String> additionalAuthorizationParams(AuthenticationContext context) {
        return Map.of("response_mode", "form_post");
    }

    @Override
    protected OAuthIdentity extractIdentity(OAuthTokenResponse tokens) {
        if (tokens.idToken() == null) {
            throw new OAuthException("Apple ID token is required but not present");
        }
        Map<String, Object> claims = JwtUtils.parsePayload(tokens.idToken());

        String sub = (String) claims.get("sub");
        if (sub == null) {
            throw new OAuthException("Apple ID token missing 'sub' claim");
        }

        String email = (String) claims.get("email");
        return new OAuthIdentity(sub, email, null);
    }



    public record AppleConfig(
        String clientId,
        String teamId,
        String keyId,
        String privateKey,
        String redirectUri
    ) implements OAuthConfig {
        // Apple uses a signed JWT as the client secret, generated from the team's private key
        @Override
        public String clientSecret() {
            return privateKey;
        }

        @Override
        public Set<String> scopes() {
            return DEFAULT_SCOPES;
        }
    }
}
