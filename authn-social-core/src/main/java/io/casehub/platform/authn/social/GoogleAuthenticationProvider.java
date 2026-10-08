package io.casehub.platform.authn.social;

import io.casehub.platform.api.authn.AuthenticationContext;
import io.casehub.platform.api.authn.AuthenticationEventListener;
import io.casehub.platform.api.authn.IdentityBindingStore;
import io.casehub.platform.api.authn.OAuthTokenStore;
import io.casehub.platform.api.authn.UserResolver;

import java.util.Map;
import java.util.Set;

public class GoogleAuthenticationProvider extends AbstractOAuthAuthenticationProvider {

    private static final String METHOD = "google";
    private static final String AUTHORIZATION_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token";
    private static final String USERINFO_ENDPOINT = "https://openidconnect.googleapis.com/v1/userinfo";
    private static final Set<String> DEFAULT_SCOPES = Set.of("openid", "email", "profile");

    private final OAuthHttpClient httpClient;

    public GoogleAuthenticationProvider(GoogleConfig config,
                                        OAuthHttpClient httpClient,
                                        OAuthTokenStore tokenStore,
                                        IdentityBindingStore bindingStore,
                                        UserResolver userResolver,
                                        AuthenticationEventListener eventListener) {
        super(config, httpClient, tokenStore, bindingStore, userResolver, eventListener);
        this.httpClient = httpClient;
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
        if (context.hints().containsKey("additionalScopes")) {
            return Map.of("access_type", "offline", "prompt", "consent");
        }
        return Map.of();
    }

    @Override
    protected OAuthIdentity extractIdentity(OAuthTokenResponse tokens) {
        if (tokens.idToken() != null) {
            return extractFromIdToken(tokens.idToken());
        }
        return extractFromUserInfo(tokens.accessToken());
    }

    private OAuthIdentity extractFromIdToken(String idToken) {
        Map<String, Object> claims = JwtUtils.parsePayload(idToken);

        String sub = (String) claims.get("sub");
        if (sub == null) {
            throw new OAuthException("Google ID token missing 'sub' claim");
        }

        String email = (String) claims.get("email");
        Object emailVerified = claims.get("email_verified");
        if (email != null && !Boolean.TRUE.equals(emailVerified)) {
            throw new OAuthException("Google email not verified for: " + email);
        }

        String name = (String) claims.get("name");
        return new OAuthIdentity(sub, email, name);
    }

    private OAuthIdentity extractFromUserInfo(String accessToken) {
        Map<String, Object> userInfo = httpClient.fetchUserInfo(USERINFO_ENDPOINT, accessToken);

        String sub = (String) userInfo.get("sub");
        if (sub == null) {
            throw new OAuthException("Google userinfo missing 'sub' field");
        }

        String email = (String) userInfo.get("email");
        Object emailVerified = userInfo.get("email_verified");
        if (email != null && !Boolean.TRUE.equals(emailVerified)) {
            throw new OAuthException("Google email not verified for: " + email);
        }

        String name = (String) userInfo.get("name");
        return new OAuthIdentity(sub, email, name);
    }



    public record GoogleConfig(
        String clientId,
        String clientSecret,
        String redirectUri
    ) implements OAuthConfig {
        @Override
        public Set<String> scopes() {
            return DEFAULT_SCOPES;
        }
    }
}
