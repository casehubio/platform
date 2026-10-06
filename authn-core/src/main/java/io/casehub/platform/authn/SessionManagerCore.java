package io.casehub.platform.authn;

import io.casehub.platform.api.authn.AuthenticationResult;
import io.casehub.platform.api.authn.InvalidCredentialException;
import io.casehub.platform.api.authn.RefreshTokenRecord;
import io.casehub.platform.api.authn.RefreshTokenStore;
import io.casehub.platform.api.authn.SessionRecord;
import io.casehub.platform.api.authn.SessionStore;
import io.casehub.platform.api.authn.TokenPair;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

public class SessionManagerCore {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();

    private final SessionStore sessionStore;
    private final RefreshTokenStore refreshTokenStore;
    private final JwtIssuerCore jwtIssuer;
    private final long accessTokenTtlSeconds;
    private final long refreshTokenTtlSeconds;

    public SessionManagerCore(SessionStore sessionStore, RefreshTokenStore refreshTokenStore,
                              JwtIssuerCore jwtIssuer, long accessTokenTtlSeconds,
                              long refreshTokenTtlSeconds) {
        this.sessionStore = sessionStore;
        this.refreshTokenStore = refreshTokenStore;
        this.jwtIssuer = jwtIssuer;
        this.accessTokenTtlSeconds = accessTokenTtlSeconds;
        this.refreshTokenTtlSeconds = refreshTokenTtlSeconds;
    }

    public TokenPair createSession(AuthenticationResult result) {
        var sessionId = UUID.randomUUID().toString();
        var familyId = UUID.randomUUID().toString();
        var now = Instant.now();

        var session = new SessionRecord(
                sessionId, result.principal().value(), result.tenancyId(),
                result.groups(), result.method(), generateCsrfToken(),
                now, now.plusSeconds(accessTokenTtlSeconds), null);
        sessionStore.store(session);

        var refreshToken = generateOpaqueToken();
        var refreshRecord = new RefreshTokenRecord(
                refreshToken, familyId, result.principal().value(), result.tenancyId(),
                result.groups(), result.method(),
                now, now.plusSeconds(refreshTokenTtlSeconds), false);
        refreshTokenStore.store(refreshRecord);

        var accessToken = jwtIssuer.issue(
                result.tenancyId(), sessionId, result.principal().value(),
                result.groups(), result.method(), accessTokenTtlSeconds);

        return new TokenPair(accessToken, refreshToken, accessTokenTtlSeconds);
    }

    public TokenPair refreshSession(String refreshToken) {
        var record = refreshTokenStore.findByToken(refreshToken)
                .orElseThrow(() -> new InvalidCredentialException("refresh", "Invalid refresh token"));

        if (record.consumed()) {
            refreshTokenStore.revokeFamily(record.familyId());
            throw new InvalidCredentialException("refresh", "Refresh token reuse detected — family revoked");
        }

        if (record.expiresAt() != null && record.expiresAt().isBefore(Instant.now())) {
            throw new InvalidCredentialException("refresh", "Refresh token expired");
        }

        refreshTokenStore.consume(refreshToken);

        var now = Instant.now();
        var newRefreshToken = generateOpaqueToken();
        var newRefreshRecord = new RefreshTokenRecord(
                newRefreshToken, record.familyId(), record.actorId(), record.tenancyId(),
                record.groups(), record.authMethod(),
                now, now.plusSeconds(refreshTokenTtlSeconds), false);
        refreshTokenStore.store(newRefreshRecord);

        var sessionId = UUID.randomUUID().toString();
        var session = new SessionRecord(
                sessionId, record.actorId(), record.tenancyId(),
                record.groups(), record.authMethod(), generateCsrfToken(),
                now, now.plusSeconds(accessTokenTtlSeconds), null);
        sessionStore.store(session);

        var accessToken = jwtIssuer.issue(
                record.tenancyId(), sessionId, record.actorId(),
                record.groups(), record.authMethod(), accessTokenTtlSeconds);

        return new TokenPair(accessToken, newRefreshToken, accessTokenTtlSeconds);
    }

    public void revokeSession(String sessionId) {
        sessionStore.delete(sessionId);
    }

    public void revokeByActorId(String actorId, String tenancyId) {
        sessionStore.deleteByActorId(actorId, tenancyId);
        refreshTokenStore.revokeByActorId(actorId, tenancyId);
    }

    private static String generateOpaqueToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return B64.encodeToString(bytes);
    }

    private static String generateCsrfToken() {
        byte[] bytes = new byte[16];
        SECURE_RANDOM.nextBytes(bytes);
        return B64.encodeToString(bytes);
    }
}
