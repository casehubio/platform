package io.casehub.platform.authn.social;

import io.casehub.platform.api.authn.AuthenticationEventListener;
import io.casehub.platform.api.authn.IdentityBindingStore;
import io.casehub.platform.api.authn.OAuthTokenStore;
import io.casehub.platform.api.authn.UserResolver;

import java.util.Map;
import java.util.Set;

public class GitHubAuthenticationProvider extends AbstractOAuthAuthenticationProvider {

    private static final String METHOD = "github";
    private static final String AUTHORIZATION_ENDPOINT = "https://github.com/login/oauth/authorize";
    private static final String TOKEN_ENDPOINT = "https://github.com/login/oauth/access_token";
    private static final String USER_API_ENDPOINT = "https://api.github.com/user";
    private static final Set<String> DEFAULT_SCOPES = Set.of("user:email");

    private final OAuthHttpClient httpClient;

    public GitHubAuthenticationProvider(GitHubConfig config,
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
    protected OAuthIdentity extractIdentity(OAuthTokenResponse tokens) {
        Map<String, Object> user = httpClient.fetchUserInfo(USER_API_ENDPOINT, tokens.accessToken());

        Object idObj = user.get("id");
        if (idObj == null) {
            throw new OAuthException("GitHub user API missing 'id' field");
        }
        String externalId = String.valueOf(idObj);

        String email = (String) user.get("email");
        String name = (String) user.get("name");
        if (name == null) {
            name = (String) user.get("login");
        }

        return new OAuthIdentity(externalId, email, name);
    }

    public record GitHubConfig(
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
