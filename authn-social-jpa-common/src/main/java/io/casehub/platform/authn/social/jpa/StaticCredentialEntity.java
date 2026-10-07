package io.casehub.platform.authn.social.jpa;

import io.casehub.platform.api.authn.CredentialType;
import io.casehub.platform.api.authn.StaticCredentialRecord;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "authn_static_credential")
@IdClass(StaticCredentialId.class)
public class StaticCredentialEntity {

    @Id
    @Column(name = "actor_id", nullable = false)
    public String actorId;

    @Id
    @Column(nullable = false, length = 255)
    public String provider;

    @Id
    @Column(name = "tenancy_id", nullable = false)
    public String tenancyId;

    @Column(nullable = false, columnDefinition = "TEXT")
    public String credential;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    public CredentialType type;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    public static StaticCredentialEntity fromRecord(StaticCredentialRecord record) {
        StaticCredentialEntity entity = new StaticCredentialEntity();
        entity.actorId = record.actorId();
        entity.provider = record.provider();
        entity.tenancyId = record.tenancyId();
        entity.credential = record.credential();
        entity.type = record.type();
        entity.createdAt = record.createdAt() != null ? record.createdAt() : Instant.now();
        entity.updatedAt = record.updatedAt() != null ? record.updatedAt() : Instant.now();
        return entity;
    }

    public StaticCredentialRecord toRecord() {
        return new StaticCredentialRecord(
                actorId, tenancyId, provider,
                credential, type, createdAt, updatedAt);
    }
}
