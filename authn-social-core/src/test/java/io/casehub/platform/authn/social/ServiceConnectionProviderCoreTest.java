package io.casehub.platform.authn.social;

import io.casehub.platform.api.authn.OAuthTokenRecord;
import io.casehub.platform.api.authn.ScopeRegistry;
import io.casehub.platform.api.authn.ServiceConnectionException;
import io.casehub.platform.api.authn.ServiceConnectionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServiceConnectionProviderCoreTest {

    private OAuthTestFixtures.InMemOAuthTokenStore tokenStore;
    private StubScopeRegistry scopeRegistry;
    private OAuthTokenManagerCore tokenManager;
    private ServiceConnectionProviderCore provider;

    @BeforeEach
    void setUp() {
        tokenStore = new OAuthTestFixtures.InMemOAuthTokenStore();
        scopeRegistry = new StubScopeRegistry();
        OAuthTokenManagerCore.TokenRefreshClient noOpRefresh = (p, r) -> {
            throw new UnsupportedOperationException();
        };
        tokenManager = new OAuthTokenManagerCore(tokenStore, noOpRefresh, OAuthTestFixtures.NO_OP_LISTENER);
        provider = new ServiceConnectionProviderCore(tokenStore, tokenManager, scopeRegistry);
    }

    @Test
    void getConnectionReturnsDisconnectedWhenNoToken() {
        scopeRegistry.register("google", Set.of("drive"), Object.class);
        var conn = provider.getConnection("actor1", "google", "tenant1");
        assertThat(conn.status()).isEqualTo(ServiceConnectionStatus.DISCONNECTED);
        assertThat(conn.missingScopes()).isEqualTo(Set.of("drive"));
    }

    @Test
    void getConnectionReturnsConnectedWhenAllScopesSatisfied() {
        scopeRegistry.register("google", Set.of("drive"), Object.class);
        tokenStore.store(new OAuthTokenRecord("actor1", "tenant1", "google",
            "token", "refresh", Set.of("openid", "drive"),
            Instant.now().plusSeconds(3600), Instant.now()));
        var conn = provider.getConnection("actor1", "google", "tenant1");
        assertThat(conn.status()).isEqualTo(ServiceConnectionStatus.CONNECTED);
        assertThat(conn.missingScopes()).isEmpty();
    }

    @Test
    void getConnectionReturnsPartialWhenSomeScopes() {
        scopeRegistry.register("google", Set.of("drive", "calendar"), Object.class);
        tokenStore.store(new OAuthTokenRecord("actor1", "tenant1", "google",
            "token", "refresh", Set.of("openid", "drive"),
            Instant.now().plusSeconds(3600), Instant.now()));
        var conn = provider.getConnection("actor1", "google", "tenant1");
        assertThat(conn.status()).isEqualTo(ServiceConnectionStatus.PARTIAL);
        assertThat(conn.missingScopes()).isEqualTo(Set.of("calendar"));
    }

    @Test
    void getAccessTokenReturnsValidToken() {
        tokenStore.store(new OAuthTokenRecord("actor1", "tenant1", "google",
            "token123", "refresh", Set.of("drive"),
            Instant.now().plusSeconds(3600), Instant.now()));
        var accessToken = provider.getAccessToken("actor1", "google", "tenant1");
        assertThat(accessToken.accessToken()).isEqualTo("token123");
    }

    @Test
    void getAccessTokenThrowsWhenNoConnection() {
        scopeRegistry.register("google", Set.of("drive"), Object.class);
        assertThatThrownBy(() -> provider.getAccessToken("actor1", "google", "tenant1"))
                .isInstanceOf(ServiceConnectionException.class)
                .satisfies(ex -> {
                    var sce = (ServiceConnectionException) ex;
                    assertThat(sce.provider()).isEqualTo("google");
                    assertThat(sce.requiredScopes()).isEqualTo(Set.of("drive"));
                });
    }

    @Test
    void disconnectDelegatesToTokenManager() {
        tokenStore.store(new OAuthTokenRecord("actor1", "tenant1", "google",
            "token", "refresh", Set.of("drive"),
            Instant.now().plusSeconds(3600), Instant.now()));
        provider.disconnect("actor1", "google", "tenant1");
        assertThat(tokenStore.findByActorId("actor1", "google", "tenant1")).isEmpty();
    }

    @Test
    void listConnectionsIncludesDisconnectedProviders() {
        scopeRegistry.register("google", Set.of("drive"), Object.class);
        scopeRegistry.register("github", Set.of("repo"), Object.class);
        tokenStore.store(new OAuthTokenRecord("actor1", "tenant1", "google",
            "token", "refresh", Set.of("openid", "drive"),
            Instant.now().plusSeconds(3600), Instant.now()));
        var connections = provider.listConnections("actor1", "tenant1");
        assertThat(connections).hasSize(2);
        var google = connections.stream().filter(c -> c.provider().equals("google")).findFirst().orElseThrow();
        var github = connections.stream().filter(c -> c.provider().equals("github")).findFirst().orElseThrow();
        assertThat(google.status()).isEqualTo(ServiceConnectionStatus.CONNECTED);
        assertThat(github.status()).isEqualTo(ServiceConnectionStatus.DISCONNECTED);
    }

    @Test
    void missingScopesReturnsCorrectSet() {
        scopeRegistry.register("google", Set.of("drive", "calendar"), Object.class);
        tokenStore.store(new OAuthTokenRecord("actor1", "tenant1", "google",
            "token", "refresh", Set.of("drive"),
            Instant.now().plusSeconds(3600), Instant.now()));
        assertThat(provider.missingScopes("actor1", "google", "tenant1")).isEqualTo(Set.of("calendar"));
    }

    private static class StubScopeRegistry implements ScopeRegistry {
        private final ConcurrentHashMap<String, Set<String>> regs = new ConcurrentHashMap<>();
        @Override public void register(String p, Set<String> s, Class<?> c) {
            regs.merge(p, s, (a, b) -> { var m = new HashSet<>(a); m.addAll(b); return Set.copyOf(m); });
        }
        @Override public Set<String> requiredScopes(String p) { return regs.getOrDefault(p, Set.of()); }
        @Override public Set<String> requiredScopes(String p, Class<?> c) { return requiredScopes(p); }
        @Override public boolean satisfies(String p, Set<String> g) { return g.containsAll(requiredScopes(p)); }
        @Override public Set<String> missingScopes(String p, Set<String> g) { var m = new HashSet<>(requiredScopes(p)); m.removeAll(g); return Set.copyOf(m); }
        @Override public Set<String> registeredProviders() { return Set.copyOf(regs.keySet()); }
    }
}
