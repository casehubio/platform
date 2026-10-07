package io.casehub.platform.authn.social.jpa;

import io.casehub.platform.api.authn.OAuthTokenRecord;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Table(name = "authn_oauth_token")
@IdClass(OAuthTokenId.class)
public class OAuthTokenEntity {

    @Id
    @Column(name = "actor_id", nullable = false)
    public String actorId;

    @Id
    @Column(nullable = false, length = 100)
    public String provider;

    @Id
    @Column(name = "tenancy_id", nullable = false)
    public String tenancyId;

    @Column(name = "access_token", nullable = false, columnDefinition = "TEXT")
    public String accessToken;

    @Column(name = "refresh_token", columnDefinition = "TEXT")
    public String refreshToken;

    @Column(name = "granted_scopes_json", columnDefinition = "TEXT")
    public String grantedScopesJson;

    @Column(name = "expires_at")
    public Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    public static OAuthTokenEntity fromRecord(OAuthTokenRecord record) {
        OAuthTokenEntity entity = new OAuthTokenEntity();
        entity.actorId          = record.actorId();
        entity.provider         = record.provider();
        entity.tenancyId        = record.tenancyId();
        entity.accessToken      = record.accessToken();
        entity.refreshToken     = record.refreshToken();
        entity.grantedScopesJson = scopesToString(record.grantedScopes());
        entity.expiresAt        = record.expiresAt();
        entity.createdAt        = record.createdAt() != null ? record.createdAt() : Instant.now();
        return entity;
    }

    public OAuthTokenRecord toRecord() {
        return new OAuthTokenRecord(
                actorId, tenancyId, provider, accessToken, refreshToken,
                stringToScopes(grantedScopesJson), expiresAt, createdAt);
    }

    public static String scopesToString(Set<String> scopes) {
        if (scopes == null || scopes.isEmpty()) {return null;}
        return String.join(",", scopes);
    }

    static Set<String> stringToScopes(String json) {
        if (json == null || json.isBlank()) return Set.of();
        return Arrays.stream(json.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }
}
