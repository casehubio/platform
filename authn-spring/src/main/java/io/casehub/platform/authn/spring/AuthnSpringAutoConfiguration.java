package io.casehub.platform.authn.spring;

import io.casehub.platform.api.authn.AuthenticationEventListener;
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
    public AuthenticationEventListener authenticationEventListener() {
        return new AuthenticationEventListener() {};
    }

    @Bean
    @ConditionalOnMissingBean
    public JwtSigningKeyResolver jwtSigningKeyResolver() {
        return new JwtSigningKeyResolver() {
            @Override
            public java.security.KeyPair signingKeyPair(String tenancyId) {
                throw new UnsupportedOperationException("No JWT signing key configured");
            }

            @Override
            public String keyId(String tenancyId) {
                throw new UnsupportedOperationException("No JWT signing key configured");
            }

            @Override
            public java.util.List<io.casehub.platform.api.authn.PublicKeyDescriptor> publicKeys() {
                return java.util.List.of();
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean
    public UserResolver userResolver() {
        return (email, tenancyId) -> java.util.Optional.empty();
    }

    @Bean
    @ConditionalOnMissingBean
    public ChallengeStore challengeStore() {
        return new ChallengeStore() {
            private final java.util.concurrent.ConcurrentHashMap<String, io.casehub.platform.api.authn.ChallengeRecord> store = new java.util.concurrent.ConcurrentHashMap<>();

            @Override
            public void store(io.casehub.platform.api.authn.ChallengeRecord record) {
                store.put(record.challengeId(), record);
            }

            @Override
            public java.util.Optional<io.casehub.platform.api.authn.ChallengeRecord> consume(String challengeId) {
                return java.util.Optional.ofNullable(store.remove(challengeId));
            }
        };
    }


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
            UserResolver userResolver,
            AuthenticationEventListener eventListener) {
        var providers = new ArrayList<AuthenticationProvider>();

        var wc = props.getWebauthn();
        if (wc != null && wc.getRpId() != null) {
            WebAuthnConfig webAuthnConfig = new WebAuthnConfig() {
                @Override
                public String rpId()                  {return wc.getRpId();}

                @Override
                public String rpName()                {return wc.getRpName();}

                @Override
                public Set<String> allowedOrigins()   {return wc.getAllowedOrigins();}

                @Override
                public long challengeTimeoutSeconds() {return wc.getChallengeTimeoutSeconds();}

                @Override
                public int challengeLength()          {return wc.getChallengeLength();}
            };
            providers.add(new WebAuthnAuthenticationProvider(webAuthnConfig, credentialStore, userResolver));
        }

        var social = props.getSocial();
        if (social.containsKey("google")) {
            var sc = social.get("google");
            providers.add(new GoogleAuthenticationProvider(
                    new GoogleAuthenticationProvider.GoogleConfig(
                            sc.getClientId(), sc.getClientSecret(), sc.getRedirectUri()),
                    httpClient, tokenStore, bindingStore, userResolver, eventListener));
        }
        if (social.containsKey("github")) {
            var sc = social.get("github");
            providers.add(new GitHubAuthenticationProvider(
                    new GitHubAuthenticationProvider.GitHubConfig(
                            sc.getClientId(), sc.getClientSecret(), sc.getRedirectUri()),
                    httpClient, tokenStore, bindingStore, userResolver, eventListener));
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
                    httpClient, tokenStore, bindingStore, userResolver, eventListener));
        }

        return List.copyOf(providers);
    }

    @Bean
    @ConditionalOnMissingBean
    public AuthenticationRouterCore authenticationRouterCore(
            List<AuthenticationProvider> providers,
            ChallengeStore challengeStore,
            AuthenticationEventListener eventListener) {
        return new AuthenticationRouterCore(providers, challengeStore, eventListener);
    }

    @Bean
    @ConditionalOnMissingBean
    public OAuthTokenManagerCore oAuthTokenManagerCore(OAuthTokenStore tokenStore,
                                                       AuthenticationEventListener eventListener) {
        OAuthTokenManagerCore.TokenRefreshClient noOpClient = refreshToken -> {
            throw new UnsupportedOperationException("No token refresh client configured");
        };
        return new OAuthTokenManagerCore(tokenStore, noOpClient, eventListener);
    }

    @Bean
    @ConditionalOnMissingBean
    public io.casehub.platform.authn.ScopeRegistryCore scopeRegistryCore(
            org.springframework.context.ApplicationContext applicationContext) {
        var registry = new io.casehub.platform.authn.ScopeRegistryCore();
        var beans = applicationContext.getBeansWithAnnotation(
                io.casehub.platform.api.authn.RequiresScopes.class);
        for (var entry : beans.entrySet()) {
            var targetClass = org.springframework.aop.support.AopUtils.getTargetClass(entry.getValue());
            var annotation = org.springframework.core.annotation.AnnotationUtils.findAnnotation(
                    targetClass, io.casehub.platform.api.authn.RequiresScopes.class);
            if (annotation != null) {
                registry.register(annotation.provider(),
                                  Set.of(annotation.scopes()), targetClass);
            }
        }
        return registry;
    }

    @Bean
    @ConditionalOnMissingBean
    public io.casehub.platform.authn.social.IncrementalConsentHandlerCore incrementalConsentHandlerCore(
            List<AuthenticationProvider> providers) {
        var providerMap = new java.util.HashMap<String, io.casehub.platform.authn.social.AbstractOAuthAuthenticationProvider>();
        for (var p : providers) {
            if (p instanceof io.casehub.platform.authn.social.AbstractOAuthAuthenticationProvider oauth) {
                providerMap.put(oauth.method(), oauth);
            }
        }
        return new io.casehub.platform.authn.social.IncrementalConsentHandlerCore(providerMap);
    }


}
