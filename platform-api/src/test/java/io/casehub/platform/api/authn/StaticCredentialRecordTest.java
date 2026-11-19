package io.casehub.platform.api.authn;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

class StaticCredentialRecordTest {

    @Test
    void backwardCompatibleConstructorDefaultsToActive() {
        var record = new StaticCredentialRecord(
                "actor1", "tenant1", "provider1",
                "cred", CredentialType.API_KEY,
                Instant.now(), Instant.now());
        assertThat(record.state()).isEqualTo(CredentialState.ACTIVE);
        assertThat(record.expiresAt()).isNull();
    }

    @Test
    void canonicalConstructorDefaultsNullStateToActive() {
        var record = new StaticCredentialRecord(
                "actor1", "tenant1", "provider1",
                "cred", CredentialType.API_KEY,
                Instant.now(), Instant.now(), null, null);
        assertThat(record.state()).isEqualTo(CredentialState.ACTIVE);
    }

    @Test
    void credentialIsNullable() {
        var record = new StaticCredentialRecord(
                "actor1", "tenant1", "provider1",
                null, CredentialType.API_KEY,
                Instant.now(), Instant.now(),
                CredentialState.ACTIVE, null);
        assertThat(record.credential()).isNull();
    }

    @Test
    void isExpiredReturnsFalseWhenNoExpiry() {
        var record = new StaticCredentialRecord(
                "actor1", "tenant1", "provider1",
                "cred", CredentialType.API_KEY,
                Instant.now(), Instant.now(),
                CredentialState.GRACE_PERIOD, null);
        assertThat(record.isExpired()).isFalse();
    }

    @Test
    void isExpiredReturnsTrueWhenPastExpiry() {
        var record = new StaticCredentialRecord(
                "actor1", "tenant1", "provider1",
                "cred", CredentialType.API_KEY,
                Instant.now(), Instant.now(),
                CredentialState.GRACE_PERIOD,
                Instant.now().minusSeconds(60));
        assertThat(record.isExpired()).isTrue();
    }

    @Test
    void isExpiredReturnsFalseWhenBeforeExpiry() {
        var record = new StaticCredentialRecord(
                "actor1", "tenant1", "provider1",
                "cred", CredentialType.API_KEY,
                Instant.now(), Instant.now(),
                CredentialState.GRACE_PERIOD,
                Instant.now().plusSeconds(3600));
        assertThat(record.isExpired()).isFalse();
    }

    @Test
    void requiredFieldsRejectNull() {
        assertThatThrownBy(() -> new StaticCredentialRecord(
                null, "t", "p", "c", CredentialType.API_KEY,
                Instant.now(), Instant.now()))
                .isInstanceOf(NullPointerException.class);
    }
}
