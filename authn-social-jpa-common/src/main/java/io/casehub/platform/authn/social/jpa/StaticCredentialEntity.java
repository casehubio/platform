package io.casehub.platform.authn.social.jpa;

import io.casehub.platform.api.authn.CredentialState;
import io.casehub.platform.api.authn.CredentialType;
import io.casehub.platform.api.authn.StaticCredentialRecord;
import io.casehub.platform.api.util.UUIDv7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "authn_static_credential")
public class StaticCredentialEntity {

    @Id
    @Column(nullable = false, length = 36)
    public String id;

    @Column(name = "actor_id", nullable = false)
    public String actorId;

    @Column(nullable = false, length = 255)
    public String provider;

    @Column(name = "tenancy_id", nullable = false)
    public String tenancyId;

    @Column(name = "credential_hash", nullable = false, length = 64)
    public String credentialHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    public CredentialType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public CredentialState status;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    @Column(name = "expires_at")
    public Instant expiresAt;

    public static StaticCredentialEntity fromRecord(StaticCredentialRecord record) {
        Objects.requireNonNull(record.credential(), "credential required for store");
        StaticCredentialEntity entity = new StaticCredentialEntity();
        entity.id = UUIDv7.generate();
        entity.actorId = record.actorId();
        entity.provider = record.provider();
        entity.tenancyId = record.tenancyId();
        entity.credentialHash = CredentialHasher.sha256Hex(record.credential());
        entity.type = record.type();
        entity.status = record.state();
        entity.createdAt = record.createdAt() != null ? record.createdAt() : Instant.now();
        entity.updatedAt = record.updatedAt() != null ? record.updatedAt() : Instant.now();
        entity.expiresAt = record.expiresAt();
        return entity;
    }

    public StaticCredentialRecord toRecord() {
        return new StaticCredentialRecord(
                actorId, tenancyId, provider,
                null, type, createdAt, updatedAt,
                status, expiresAt);
    }
}
