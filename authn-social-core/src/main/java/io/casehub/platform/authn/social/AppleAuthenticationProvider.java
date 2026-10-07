package io.casehub.platform.authn.social;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.platform.api.authn.AuthenticationContext;
import io.casehub.platform.api.authn.ChallengeResponse;
import io.casehub.platform.api.authn.AuthenticationEventListener;
import io.casehub.platform.api.authn.IdentityBindingStore;
import io.casehub.platform.api.authn.OAuthTokenStore;
import io.casehub.platform.api.authn.UserResolver;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Set;

public class AppleAuthenticationProvider extends AbstractOAuthAuthenticationProvider {

    private static final String METHOD = "apple";
    private static final String AUTHORIZATION_ENDPOINT = "https://appleid.apple.com/auth/authorize";
    private static final String TOKEN_ENDPOINT = "https://appleid.apple.com/auth/token";
    private static final Set<String> DEFAULT_SCOPES = Set.of("openid", "email", "name");
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

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
    public ChallengeResponse initiate(AuthenticationContext context) {
        var baseResponse = (OAuthChallengeResponse) super.initiate(context);
        String url = baseResponse.authorizationUrl()
                + "&response_mode=" + URLEncoder.encode("form_post", StandardCharsets.UTF_8);
        return new OAuthChallengeResponse(baseResponse.challengeId(), baseResponse.expiresAt(), url);
    }

    @Override
    protected OAuthIdentity extractIdentity(OAuthTokenResponse tokens) {
        if (tokens.idToken() == null) {
            throw new OAuthException("Apple ID token is required but not present");
        }
        Map<String, Object> claims = parseJwtPayload(tokens.idToken());

        String sub = (String) claims.get("sub");
        if (sub == null) {
            throw new OAuthException("Apple ID token missing 'sub' claim");
        }

        String email = (String) claims.get("email");
        return new OAuthIdentity(sub, email, null);
    }

    private static Map<String, Object> parseJwtPayload(String jwt) {
        String[] parts = jwt.split("\\.");
        if (parts.length < 2) {
            throw new OAuthException("Invalid JWT format");
        }
        try {
            byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
            return JSON.readValue(payload, MAP_TYPE);
        } catch (IOException e) {
            throw new OAuthException("Failed to parse Apple ID token: " + e.getMessage(), e);
        }
    }

    public record AppleConfig(
        String clientId,
        String teamId,
        String keyId,
        String privateKey,
        String redirectUri
    ) implements OAuthConfig {
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
