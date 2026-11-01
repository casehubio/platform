package io.casehub.platform.registration.jpa;

import io.casehub.platform.registration.PasswordCredential;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "registration_password_credential")
@IdClass(PasswordCredentialEntityId.class)
public class PasswordCredentialEntity {

    @Id
    @Column(name = "actor_id")
    public String actorId;

    @Id
    @Column(name = "tenancy_id")
    public String tenancyId;

    @Column(name = "hashed_password", nullable = false)
    public String hashedPassword;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "last_used_at")
    public Instant lastUsedAt;

    public static PasswordCredentialEntity fromRecord(PasswordCredential record) {
        PasswordCredentialEntity e = new PasswordCredentialEntity();
        e.actorId = record.actorId();
        e.tenancyId = record.tenancyId();
        e.hashedPassword = record.hashedPassword();
        e.createdAt = record.createdAt();
        e.lastUsedAt = record.lastUsedAt();
        return e;
    }

    public PasswordCredential toRecord() {
        return new PasswordCredential(actorId, tenancyId, hashedPassword, createdAt, lastUsedAt);
    }
}
