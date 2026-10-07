package io.casehub.platform.authn.social;

import io.casehub.platform.api.authn.AuthenticationEventListener;
import io.casehub.platform.api.authn.OAuthTokenRecord;
import io.casehub.platform.api.authn.OAuthTokenStore;
import io.casehub.platform.api.authn.ScopeRegistry;
import io.casehub.platform.api.authn.ServiceConnectionException;
import io.casehub.platform.api.authn.ServiceConnectionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

class ServiceConnectionProviderCoreTest {

    private InMemoryTokenStore tokenStore;
    private StubScopeRegistry scopeRegistry;
    private OAuthTokenManagerCore tokenManager;
    private ServiceConnectionProviderCore provider;

    @BeforeEach
    void setUp() {
        tokenStore = new InMemoryTokenStore();
        scopeRegistry = new StubScopeRegistry();
        OAuthTokenManagerCore.TokenRefreshClient noOpRefresh = r -> {
            throw new UnsupportedOperationException();
        };
        tokenManager = new OAuthTokenManagerCore(tokenStore, noOpRefresh, new AuthenticationEventListener() {});
        provider = new ServiceConnectionProviderCore(tokenStore, tokenManager, scopeRegistry);
    }

    @Test
    void getConnectionReturnsDisconnectedWhenNoToken() {
        scopeRegistry.register("google", Set.of("drive"), Object.class);
        var conn = provider.getConnection("actor1", "google", "tenant1");
        assertEquals(ServiceConnectionStatus.DISCONNECTED, conn.status());
        assertEquals(Set.of("drive"), conn.missingScopes());
    }

    @Test
    void getConnectionReturnsConnectedWhenAllScopesSatisfied() {
        scopeRegistry.register("google", Set.of("drive"), Object.class);
        tokenStore.store(new OAuthTokenRecord("actor1", "tenant1", "google",
            "token", "refresh", Set.of("openid", "drive"),
            Instant.now().plusSeconds(3600), Instant.now()));
        var conn = provider.getConnection("actor1", "google", "tenant1");
        assertEquals(ServiceConnectionStatus.CONNECTED, conn.status());
        assertTrue(conn.missingScopes().isEmpty());
    }

    @Test
    void getConnectionReturnsPartialWhenSomeScopes() {
        scopeRegistry.register("google", Set.of("drive", "calendar"), Object.class);
        tokenStore.store(new OAuthTokenRecord("actor1", "tenant1", "google",
            "token", "refresh", Set.of("openid", "drive"),
            Instant.now().plusSeconds(3600), Instant.now()));
        var conn = provider.getConnection("actor1", "google", "tenant1");
        assertEquals(ServiceConnectionStatus.PARTIAL, conn.status());
        assertEquals(Set.of("calendar"), conn.missingScopes());
    }

    @Test
    void getAccessTokenReturnsValidToken() {
        tokenStore.store(new OAuthTokenRecord("actor1", "tenant1", "google",
            "token123", "refresh", Set.of("drive"),
            Instant.now().plusSeconds(3600), Instant.now()));
        var accessToken = provider.getAccessToken("actor1", "google", "tenant1");
        assertEquals("token123", accessToken.accessToken());
    }

    @Test
    void getAccessTokenThrowsWhenNoConnection() {
        scopeRegistry.register("google", Set.of("drive"), Object.class);
        var ex = assertThrows(ServiceConnectionException.class,
            () -> provider.getAccessToken("actor1", "google", "tenant1"));
        assertEquals("google", ex.provider());
        assertEquals(Set.of("drive"), ex.requiredScopes());
    }

    @Test
    void disconnectDelegatesToTokenManager() {
        tokenStore.store(new OAuthTokenRecord("actor1", "tenant1", "google",
            "token", "refresh", Set.of("drive"),
            Instant.now().plusSeconds(3600), Instant.now()));
        provider.disconnect("actor1", "google", "tenant1");
        assertTrue(tokenStore.findByActorId("actor1", "google", "tenant1").isEmpty());
    }

    @Test
    void listConnectionsIncludesDisconnectedProviders() {
        scopeRegistry.register("google", Set.of("drive"), Object.class);
        scopeRegistry.register("github", Set.of("repo"), Object.class);
        tokenStore.store(new OAuthTokenRecord("actor1", "tenant1", "google",
            "token", "refresh", Set.of("openid", "drive"),
            Instant.now().plusSeconds(3600), Instant.now()));
        var connections = provider.listConnections("actor1", "tenant1");
        assertEquals(2, connections.size());
        var google = connections.stream().filter(c -> c.provider().equals("google")).findFirst().orElseThrow();
        var github = connections.stream().filter(c -> c.provider().equals("github")).findFirst().orElseThrow();
        assertEquals(ServiceConnectionStatus.CONNECTED, google.status());
        assertEquals(ServiceConnectionStatus.DISCONNECTED, github.status());
    }

    @Test
    void missingScopesReturnsCorrectSet() {
        scopeRegistry.register("google", Set.of("drive", "calendar"), Object.class);
        tokenStore.store(new OAuthTokenRecord("actor1", "tenant1", "google",
            "token", "refresh", Set.of("drive"),
            Instant.now().plusSeconds(3600), Instant.now()));
        assertEquals(Set.of("calendar"), provider.missingScopes("actor1", "google", "tenant1"));
    }

    private static class InMemoryTokenStore implements OAuthTokenStore {
        private final ConcurrentHashMap<String, OAuthTokenRecord> store = new ConcurrentHashMap<>();
        private String key(String a, String p, String t) { return a + ":" + p + ":" + t; }
        @Override public void store(OAuthTokenRecord r) { store.put(key(r.actorId(), r.provider(), r.tenancyId()), r); }
        @Override public Optional<OAuthTokenRecord> findByActorId(String a, String p, String t) { return Optional.ofNullable(store.get(key(a, p, t))); }
        @Override public List<OAuthTokenRecord> findAllByActorId(String a, String t) { return store.values().stream().filter(r -> r.actorId().equals(a) && r.tenancyId().equals(t)).toList(); }
        @Override public void delete(String a, String p, String t) { store.remove(key(a, p, t)); }
        @Override public void updateTokens(String a, String p, String t, String at, String rt, Instant e) {}
        @Override public void updateScopes(String a, String p, String t, Set<String> s) {}
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
