package io.casehub.platform.authn.challenge.jpa;

import io.casehub.platform.api.authn.ChallengeRecord;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Arrays;

@Entity
@Table(name = "authn_challenge",
       indexes = @Index(name = "idx_authn_challenge_expires", columnList = "expires_at"))
public class ChallengeEntity {

    @Id
    @Column(name = "challenge_id")
    public String challengeId;

    @Column(name = "method", nullable = false, length = 100)
    public String method;

    @Column(name = "tenancy_id", nullable = false)
    public String tenancyId;

    @Column(name = "challenge_data")
    public byte[] challengeData;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    public Instant expiresAt;

    public static ChallengeEntity fromRecord(ChallengeRecord record) {
        ChallengeEntity e = new ChallengeEntity();
        e.challengeId = record.challengeId();
        e.method = record.method();
        e.tenancyId = record.tenancyId();
        e.challengeData = record.challengeData() != null
                ? Arrays.copyOf(record.challengeData(), record.challengeData().length)
                : null;
        e.createdAt = record.createdAt();
        e.expiresAt = record.expiresAt();
        return e;
    }

    public ChallengeRecord toRecord() {
        return new ChallengeRecord(
                challengeId,
                method,
                tenancyId,
                challengeData != null ? Arrays.copyOf(challengeData, challengeData.length) : null,
                createdAt,
                expiresAt
        );
    }
}
