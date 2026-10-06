package io.casehub.platform.authn;

import io.casehub.platform.api.authn.AuthenticationResult;
import io.casehub.platform.api.authn.InvalidCredentialException;
import io.casehub.platform.api.authn.JwtSigningKeyResolver;
import io.casehub.platform.api.authn.PublicKeyDescriptor;
import io.casehub.platform.api.authn.RefreshTokenRecord;
import io.casehub.platform.api.authn.RefreshTokenStore;
import io.casehub.platform.api.authn.SessionRecord;
import io.casehub.platform.api.authn.SessionStore;
import io.casehub.platform.api.identity.PrincipalId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SessionManagerCoreTest {

    private InMemSessionStore sessionStore;
    private InMemRefreshTokenStore refreshStore;
    private SessionManagerCore manager;

    @BeforeEach
    void setUp() {
        sessionStore = new InMemSessionStore();
        refreshStore = new InMemRefreshTokenStore();
        var keyPair = generateRsaKeyPair();
        var jwtIssuer = new JwtIssuerCore(stubResolver(keyPair));
        manager = new SessionManagerCore(sessionStore, refreshStore, jwtIssuer, 3600, 86400);
    }

    @Test
    void createSessionStoresSessionAndReturnsTokens() {
        var result = authResult("user-1", "tenant-1", "webauthn");
        var pair = manager.createSession(result);

        assertThat(pair.accessToken()).isNotEmpty();
        assertThat(pair.refreshToken()).isNotEmpty();
        assertThat(pair.expiresInSeconds()).isEqualTo(3600);
        assertThat(sessionStore.sessions).hasSize(1);
        assertThat(refreshStore.tokens).hasSize(1);
    }

    @Test
    void refreshSessionIssuesNewTokensAndConsumesOld() {
        var result = authResult("user-1", "tenant-1", "webauthn");
        var original = manager.createSession(result);

        var refreshed = manager.refreshSession(original.refreshToken());

        assertThat(refreshed.accessToken()).isNotEqualTo(original.accessToken());
        assertThat(refreshed.refreshToken()).isNotEqualTo(original.refreshToken());
        assertThat(sessionStore.sessions).hasSize(2);
        assertThat(refreshStore.tokens).hasSize(2);
    }

    @Test
    void refreshSessionThrowsOnInvalidToken() {
        assertThatThrownBy(() -> manager.refreshSession("nonexistent"))
                .isInstanceOf(InvalidCredentialException.class)
                .hasMessageContaining("Invalid refresh token");
    }

    @Test
    void refreshSessionDetectsReplayAndRevokesFamily() {
        var result = authResult("user-1", "tenant-1", "webauthn");
        var original = manager.createSession(result);
        manager.refreshSession(original.refreshToken());

        assertThatThrownBy(() -> manager.refreshSession(original.refreshToken()))
                .isInstanceOf(InvalidCredentialException.class)
                .hasMessageContaining("reuse detected");
    }

    @Test
    void refreshSessionThrowsOnExpiredToken() {
        var result = authResult("user-1", "tenant-1", "webauthn");
        var pair = manager.createSession(result);

        refreshStore.tokens.values().forEach(r -> {
            refreshStore.tokens.put(r.token(), new RefreshTokenRecord(
                    r.token(), r.familyId(), r.actorId(), r.tenancyId(),
                    r.groups(), r.authMethod(), r.createdAt(),
                    Instant.now().minusSeconds(1), false));
        });

        assertThatThrownBy(() -> manager.refreshSession(pair.refreshToken()))
                .isInstanceOf(InvalidCredentialException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void revokeSessionDeletesSession() {
        var result = authResult("user-1", "tenant-1", "webauthn");
        manager.createSession(result);
        var sessionId = sessionStore.sessions.keys().nextElement();

        manager.revokeSession(sessionId);
        assertThat(sessionStore.sessions).isEmpty();
    }

    @Test
    void revokeByActorIdDeletesAllSessionsAndTokens() {
        var result = authResult("user-1", "tenant-1", "webauthn");
        manager.createSession(result);
        manager.createSession(result);

        manager.revokeByActorId("human:user-1", "tenant-1");
        assertThat(sessionStore.sessions).isEmpty();
        assertThat(refreshStore.tokens).isEmpty();
    }

    private static AuthenticationResult authResult(String actorId, String tenancyId, String method) {
        return new AuthenticationResult(PrincipalId.human(actorId), tenancyId, Set.of("user"), method, Map.of());
    }

    private static KeyPair generateRsaKeyPair() {
        try {
            var gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            return gen.generateKeyPair();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static JwtSigningKeyResolver stubResolver(KeyPair keyPair) {
        return new JwtSigningKeyResolver() {
            @Override public KeyPair signingKeyPair(String tenancyId) { return keyPair; }
            @Override public String keyId(String tenancyId) { return "test-key"; }
            @Override public List<PublicKeyDescriptor> publicKeys() { return List.of(); }
        };
    }

    static class InMemSessionStore implements SessionStore {
        final ConcurrentHashMap<String, SessionRecord> sessions = new ConcurrentHashMap<>();

        @Override public void store(SessionRecord session) {
            sessions.put(session.sessionId(), session);
        }
        @Override public Optional<SessionRecord> findById(String sessionId) {
            return Optional.ofNullable(sessions.get(sessionId));
        }
        @Override public void delete(String sessionId) {
            sessions.remove(sessionId);
        }
        @Override public void deleteByActorId(String actorId, String tenancyId) {
            sessions.entrySet().removeIf(e ->
                    e.getValue().actorId().equals(actorId) && e.getValue().tenancyId().equals(tenancyId));
        }
    }

    static class InMemRefreshTokenStore implements RefreshTokenStore {
        final ConcurrentHashMap<String, RefreshTokenRecord> tokens = new ConcurrentHashMap<>();

        @Override public void store(RefreshTokenRecord record) {
            tokens.put(record.token(), record);
        }
        @Override public Optional<RefreshTokenRecord> findByToken(String token) {
            return Optional.ofNullable(tokens.get(token));
        }
        @Override public void consume(String token) {
            tokens.computeIfPresent(token, (k, r) -> new RefreshTokenRecord(
                    r.token(), r.familyId(), r.actorId(), r.tenancyId(),
                    r.groups(), r.authMethod(), r.createdAt(), r.expiresAt(), true));
        }
        @Override public void revokeFamily(String familyId) {
            tokens.entrySet().removeIf(e -> e.getValue().familyId().equals(familyId));
        }
        @Override public void revokeByActorId(String actorId, String tenancyId) {
            tokens.entrySet().removeIf(e ->
                    e.getValue().actorId().equals(actorId) && e.getValue().tenancyId().equals(tenancyId));
        }
        @Override public int purgeExpired(Instant before) {
            int[] count = {0};
            tokens.entrySet().removeIf(e -> {
                if (e.getValue().expiresAt() != null && e.getValue().expiresAt().isBefore(before)) {
                    count[0]++;
                    return true;
                }
                return false;
            });
            return count[0];
        }
    }
}
