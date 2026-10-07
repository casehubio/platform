package io.casehub.platform.authn.spring;

import io.casehub.platform.api.authn.AuthenticationProvider;
import io.casehub.platform.api.authn.ChallengeStore;
import io.casehub.platform.api.authn.IdentityBindingStore;
import io.casehub.platform.api.authn.JwtSigningKeyResolver;
import io.casehub.platform.api.authn.OAuthTokenStore;
import io.casehub.platform.api.authn.RefreshTokenStore;
import io.casehub.platform.api.authn.SessionStore;
import io.casehub.platform.api.authn.UserResolver;
import io.casehub.platform.api.authn.WebAuthnCredentialStore;
import io.casehub.platform.authn.AuthenticationRouterCore;
import io.casehub.platform.authn.JwtIssuerCore;
import io.casehub.platform.authn.SessionManagerCore;
import io.casehub.platform.authn.social.AppleAuthenticationProvider;
import io.casehub.platform.authn.social.GitHubAuthenticationProvider;
import io.casehub.platform.authn.social.GoogleAuthenticationProvider;
import io.casehub.platform.authn.social.JdkOAuthHttpClient;
import io.casehub.platform.authn.social.OAuthHttpClient;
import io.casehub.platform.authn.social.OAuthTokenManagerCore;
import io.casehub.platform.authn.webauthn.WebAuthnAuthenticationProvider;
import io.casehub.platform.authn.webauthn.WebAuthnConfig;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@AutoConfiguration
@EnableConfigurationProperties(AuthnSpringProperties.class)
public class AuthnSpringAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public JwtIssuerCore jwtIssuerCore(JwtSigningKeyResolver keyResolver) {
        return new JwtIssuerCore(keyResolver);
    }

    @Bean
    @ConditionalOnMissingBean
    public SessionManagerCore sessionManagerCore(SessionStore sessionStore,
                                                  RefreshTokenStore refreshTokenStore,
                                                  JwtIssuerCore jwtIssuer,
                                                  AuthnSpringProperties props) {
        return new SessionManagerCore(sessionStore, refreshTokenStore, jwtIssuer,
                props.getAccessTokenTtlSeconds(), props.getRefreshTokenTtlSeconds());
    }

    @Bean
    @ConditionalOnMissingBean
    public OAuthHttpClient oAuthHttpClient() {
        return new JdkOAuthHttpClient();
    }

    @Bean
    @ConditionalOnMissingBean(name = "authenticationProviders")
    public List<AuthenticationProvider> authenticationProviders(
            AuthnSpringProperties props,
            WebAuthnCredentialStore credentialStore,
            OAuthHttpClient httpClient,
            OAuthTokenStore tokenStore,
            IdentityBindingStore bindingStore,
            UserResolver userResolver) {
        var providers = new ArrayList<AuthenticationProvider>();

        var wc = props.getWebauthn();
        if (wc != null && wc.getRpId() != null) {
            WebAuthnConfig webAuthnConfig = new WebAuthnConfig() {
                @Override public String rpId() { return wc.getRpId(); }
                @Override public String rpName() { return wc.getRpName(); }
                @Override public Set<String> allowedOrigins() { return wc.getAllowedOrigins(); }
                @Override public long challengeTimeoutSeconds() { return wc.getChallengeTimeoutSeconds(); }
                @Override public int challengeLength() { return wc.getChallengeLength(); }
            };
            providers.add(new WebAuthnAuthenticationProvider(webAuthnConfig, credentialStore, userResolver));
        }

        var social = props.getSocial();
        if (social.containsKey("google")) {
            var sc = social.get("google");
            providers.add(new GoogleAuthenticationProvider(
                    new GoogleAuthenticationProvider.GoogleConfig(
                            sc.getClientId(), sc.getClientSecret(), sc.getRedirectUri()),
                    httpClient, tokenStore, bindingStore, userResolver));
        }
        if (social.containsKey("github")) {
            var sc = social.get("github");
            providers.add(new GitHubAuthenticationProvider(
                    new GitHubAuthenticationProvider.GitHubConfig(
                            sc.getClientId(), sc.getClientSecret(), sc.getRedirectUri()),
                    httpClient, tokenStore, bindingStore, userResolver));
        }
        if (social.containsKey("apple")) {
            var sc = social.get("apple");
            providers.add(new AppleAuthenticationProvider(
                    new AppleAuthenticationProvider.AppleConfig(
                            sc.getClientId(),
                            sc.getTeamId() != null ? sc.getTeamId() : "",
                            sc.getKeyId() != null ? sc.getKeyId() : "",
                            sc.getPrivateKey() != null ? sc.getPrivateKey() : "",
                            sc.getRedirectUri()),
                    httpClient, tokenStore, bindingStore, userResolver));
        }

        return List.copyOf(providers);
    }

    @Bean
    @ConditionalOnMissingBean
    public AuthenticationRouterCore authenticationRouterCore(
            List<AuthenticationProvider> providers,
            ChallengeStore challengeStore) {
        return new AuthenticationRouterCore(providers, challengeStore);
    }

    @Bean
    @ConditionalOnMissingBean
    public OAuthTokenManagerCore oAuthTokenManagerCore(OAuthTokenStore tokenStore) {
        OAuthTokenManagerCore.TokenRefreshClient noOpClient = refreshToken -> {
            throw new UnsupportedOperationException("No token refresh client configured");
        };
        return new OAuthTokenManagerCore(tokenStore, noOpClient);
    }
}
