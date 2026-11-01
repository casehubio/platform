package io.casehub.platform.registration.jpa;

import io.casehub.platform.registration.PasswordResetToken;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "registration_password_reset_token")
public class PasswordResetTokenEntity {

    @Id
    @Column(name = "token_hash", length = 64)
    public String tokenHash;

    @Column(name = "user_id", nullable = false)
    public String userId;

    @Column(name = "tenancy_id", nullable = false)
    public String tenancyId;

    @Column(name = "expires_at", nullable = false)
    public Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    public static PasswordResetTokenEntity fromToken(PasswordResetToken token) {
        PasswordResetTokenEntity e = new PasswordResetTokenEntity();
        e.tokenHash = token.tokenHash();
        e.userId = token.userId();
        e.tenancyId = token.tenancyId();
        e.expiresAt = token.expiresAt();
        e.createdAt = token.createdAt();
        return e;
    }

    public PasswordResetToken toToken() {
        return new PasswordResetToken(tokenHash, userId, tenancyId, expiresAt, createdAt);
    }
}
