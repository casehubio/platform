package io.casehub.platform.authn.quarkus;

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
import io.casehub.platform.authn.social.HttpTokenRefreshClient;
import io.casehub.platform.authn.social.OAuthHttpClient;
import io.casehub.platform.authn.social.OAuthTokenManagerCore;
import io.casehub.platform.authn.webauthn.WebAuthnAuthenticationProvider;
import io.casehub.platform.authn.webauthn.WebAuthnConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;

import java.util.Set;

@ApplicationScoped
public class AuthnBeans {

    @Produces
    @ApplicationScoped
    public JwtIssuerCore jwtIssuerCore(JwtSigningKeyResolver keyResolver) {
        return new JwtIssuerCore(keyResolver);
    }

    @Produces
    @ApplicationScoped
    public SessionManagerCore sessionManagerCore(SessionStore sessionStore,
                                                  RefreshTokenStore refreshTokenStore,
                                                  JwtIssuerCore jwtIssuer,
                                                  AuthnConfig config) {
        return new SessionManagerCore(sessionStore, refreshTokenStore, jwtIssuer,
                config.accessTokenTtlSeconds(), config.refreshTokenTtlSeconds());
    }

    @Produces
    @ApplicationScoped
    public OAuthHttpClient oAuthHttpClient() {
        return new JdkOAuthHttpClient();
    }

    @Produces
    @Singleton
    public List<AuthenticationProvider> authenticationProviders(
            AuthnConfig config,
            WebAuthnCredentialStore credentialStore,
            OAuthHttpClient httpClient,
            OAuthTokenStore tokenStore,
            IdentityBindingStore bindingStore,
            UserResolver userResolver,
            AuthenticationEventListener eventListener) {
        var providers = new ArrayList<AuthenticationProvider>();

        config.webauthn().ifPresent(wc -> {
            WebAuthnConfig webAuthnConfig = new WebAuthnConfig() {
                @Override
                public String rpId()                  {return wc.rpId();}

                @Override
                public String rpName()                {return wc.rpName();}

                @Override
                public Set<String> allowedOrigins()   {return wc.allowedOrigins();}

                @Override
                public long challengeTimeoutSeconds() {return wc.challengeTimeoutSeconds();}

                @Override
                public int challengeLength()          {return wc.challengeLength();}
            };
            providers.add(new WebAuthnAuthenticationProvider(webAuthnConfig, credentialStore, userResolver));
        });

        var social = config.social();
        if (social.containsKey("google")) {
            var sc = social.get("google");
            providers.add(new GoogleAuthenticationProvider(
                    new GoogleAuthenticationProvider.GoogleConfig(
                            sc.clientId(), sc.clientSecret(), sc.redirectUri()),
                    httpClient, tokenStore, bindingStore, userResolver, eventListener));
        }
        if (social.containsKey("github")) {
            var sc = social.get("github");
            providers.add(new GitHubAuthenticationProvider(
                    new GitHubAuthenticationProvider.GitHubConfig(
                            sc.clientId(), sc.clientSecret(), sc.redirectUri()),
                    httpClient, tokenStore, bindingStore, userResolver, eventListener));
        }
        if (social.containsKey("apple")) {
            var sc = social.get("apple");
            providers.add(new AppleAuthenticationProvider(
                    new AppleAuthenticationProvider.AppleConfig(
                            sc.clientId(),
                            sc.teamId().orElse(""),
                            sc.keyId().orElse(""),
                            sc.privateKey().orElse(""),
                            sc.redirectUri()),
                    httpClient, tokenStore, bindingStore, userResolver, eventListener));
        }

        return List.copyOf(providers);
    }

    @Produces
    @ApplicationScoped
    public AuthenticationRouterCore authenticationRouterCore(
            List<AuthenticationProvider> providers,
            ChallengeStore challengeStore,
            AuthenticationEventListener eventListener,
            io.casehub.platform.authn.ScopeMergingLoginCustomizer scopeMergingCustomizer) {
        return new AuthenticationRouterCore(providers, challengeStore, eventListener, scopeMergingCustomizer);
    }

    @Produces
    @ApplicationScoped
    public OAuthTokenManagerCore oAuthTokenManagerCore(OAuthTokenStore tokenStore,
                                                       OAuthHttpClient httpClient,
                                                       AuthnConfig config,
                                                       AuthenticationEventListener eventListener) {
        var refreshConfigs = new java.util.LinkedHashMap<String, HttpTokenRefreshClient.ProviderRefreshConfig>();
        var social         = config.social();
        if (social.containsKey("google")) {
            var sc = social.get("google");
            refreshConfigs.put("google", new HttpTokenRefreshClient.ProviderRefreshConfig(
                    "https://oauth2.googleapis.com/token", sc.clientId(), sc.clientSecret()));
        }
        if (social.containsKey("github")) {
            var sc = social.get("github");
            refreshConfigs.put("github", new HttpTokenRefreshClient.ProviderRefreshConfig(
                    "https://github.com/login/oauth/access_token", sc.clientId(), sc.clientSecret()));
        }
        if (social.containsKey("apple")) {
            var sc = social.get("apple");
            refreshConfigs.put("apple", new HttpTokenRefreshClient.ProviderRefreshConfig(
                    "https://appleid.apple.com/auth/token", sc.clientId(), sc.clientSecret()));
        }

        var refreshClient = refreshConfigs.isEmpty()
                            ? (OAuthTokenManagerCore.TokenRefreshClient) (provider, refreshToken) -> {
            throw new UnsupportedOperationException("No token refresh client configured");
        }
                            : new HttpTokenRefreshClient(httpClient, refreshConfigs);

        return new OAuthTokenManagerCore(tokenStore, refreshClient, eventListener);
    }

    @Produces
    @ApplicationScoped
    public io.casehub.platform.authn.ScopeRegistryCore scopeRegistryCore(
            jakarta.enterprise.inject.spi.BeanManager beanManager) {
        var registry = new io.casehub.platform.authn.ScopeRegistryCore();
        for (var bean : beanManager.getBeans(Object.class)) {
            var annotation = bean.getBeanClass().getAnnotation(io.casehub.platform.api.authn.RequiresScopes.class);
            if (annotation != null) {
                registry.register(annotation.provider(), Set.of(annotation.scopes()), bean.getBeanClass());
            }
        }
        return registry;
    }

    @Produces
    @ApplicationScoped
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

    @Produces
    @ApplicationScoped
    public io.casehub.platform.authn.ScopeMergingLoginCustomizer scopeMergingLoginCustomizer(
            io.casehub.platform.authn.ScopeRegistryCore scopeRegistry,
            AuthnConfig config) {
        return new io.casehub.platform.authn.ScopeMergingLoginCustomizer(scopeRegistry, config.mergeServiceScopes());
    }

    @Produces
    @ApplicationScoped
    public io.casehub.platform.authn.social.ServiceConnectionProviderCore serviceConnectionProviderCore(
            OAuthTokenStore tokenStore,
            OAuthTokenManagerCore tokenManager,
            io.casehub.platform.authn.ScopeRegistryCore scopeRegistry) {
        return new io.casehub.platform.authn.social.ServiceConnectionProviderCore(tokenStore, tokenManager, scopeRegistry);
    }


}
