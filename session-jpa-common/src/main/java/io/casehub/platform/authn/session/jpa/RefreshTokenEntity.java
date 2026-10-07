package io.casehub.platform.authn.session.jpa;

import io.casehub.platform.api.authn.RefreshTokenRecord;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Set;

@Entity
@Table(name = "authn_refresh_token",
       indexes = {
               @Index(name = "idx_authn_refresh_token_family",
                      columnList = "family_id"),
               @Index(name = "idx_authn_refresh_token_tenant_actor",
                      columnList = "tenancy_id, actor_id"),
               @Index(name = "idx_authn_refresh_token_expires",
                      columnList = "expires_at")
       })
public class RefreshTokenEntity {

    @Id
    @Column(name = "token", length = 500)
    public String token;

    @Column(name = "family_id", nullable = false)
    public String familyId;

    @Column(name = "actor_id", nullable = false)
    public String actorId;

    @Column(name = "tenancy_id", nullable = false)
    public String tenancyId;

    @Column(name = "groups_json", columnDefinition = "TEXT")
    public String groupsJson;

    @Column(name = "auth_method", nullable = false, length = 100)
    public String authMethod;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    public Instant expiresAt;

    @Column(name = "consumed", nullable = false)
    public boolean consumed;

    public static RefreshTokenEntity fromRecord(RefreshTokenRecord record) {
        RefreshTokenEntity e = new RefreshTokenEntity();
        e.token = record.token();
        e.familyId = record.familyId();
        e.actorId = record.actorId();
        e.tenancyId = record.tenancyId();
        e.groupsJson = SessionEntity.groupsToString(record.groups());
        e.authMethod = record.authMethod();
        e.createdAt = record.createdAt();
        e.expiresAt = record.expiresAt();
        e.consumed = record.consumed();
        return e;
    }

    public RefreshTokenRecord toRecord() {
        return new RefreshTokenRecord(
                token,
                familyId,
                actorId,
                tenancyId,
                SessionEntity.stringToGroups(groupsJson),
                authMethod,
                createdAt,
                expiresAt,
                consumed
        );
    }
}
