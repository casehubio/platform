package io.casehub.platform.authn.webauthn.jpa;

import io.casehub.platform.api.authn.WebAuthnCredential;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class WebAuthnCredentialEntityTest {

    @Test
    void roundTrip_preservesAllFields() {
        var now = Instant.now();
        var credential = new WebAuthnCredential(
                "cred-1", "alice", "tenant-1",
                new byte[]{1, 2, 3, 4}, 42L,
                Set.of("usb", "nfc"), "aaguid-1", "My Key",
                now, now.plusSeconds(60), true
        );

        WebAuthnCredentialEntity entity = WebAuthnCredentialEntity.fromRecord(credential);
        WebAuthnCredential result = entity.toRecord();

        assertThat(result.credentialId()).isEqualTo("cred-1");
        assertThat(result.actorId()).isEqualTo("alice");
        assertThat(result.tenancyId()).isEqualTo("tenant-1");
        assertThat(result.publicKeyCose()).isEqualTo(new byte[]{1, 2, 3, 4});
        assertThat(result.signCount()).isEqualTo(42L);
        assertThat(result.transports()).containsExactlyInAnyOrder("usb", "nfc");
        assertThat(result.aaguid()).isEqualTo("aaguid-1");
        assertThat(result.displayName()).isEqualTo("My Key");
        assertThat(result.createdAt()).isEqualTo(now);
        assertThat(result.lastUsedAt()).isEqualTo(now.plusSeconds(60));
        assertThat(result.discoverable()).isTrue();
    }

    @Test
    void roundTrip_handlesNullPublicKey() {
        var credential = new WebAuthnCredential(
                "cred-2", "bob", "tenant-1",
                null, 0L, Set.of(), null, null,
                Instant.now(), null, false
        );

        WebAuthnCredentialEntity entity = WebAuthnCredentialEntity.fromRecord(credential);
        WebAuthnCredential result = entity.toRecord();

        assertThat(result.publicKeyCose()).isNull();
        assertThat(result.transports()).isEmpty();
        assertThat(result.aaguid()).isNull();
        assertThat(result.displayName()).isNull();
        assertThat(result.lastUsedAt()).isNull();
        assertThat(result.discoverable()).isFalse();
    }

    @Test
    void defensiveCopy_publicKeyCose_fromRecord() {
        byte[] original = {1, 2, 3};
        var credential = new WebAuthnCredential(
                "cred-3", "alice", "tenant-1",
                original, 0L, Set.of(), null, null,
                Instant.now(), null, false
        );

        WebAuthnCredentialEntity entity = WebAuthnCredentialEntity.fromRecord(credential);
        original[0] = 99;

        assertThat(entity.publicKeyCose[0]).isEqualTo((byte) 1);
    }

    @Test
    void defensiveCopy_publicKeyCose_toRecord() {
        var entity = new WebAuthnCredentialEntity();
        entity.credentialId = "cred-4";
        entity.actorId = "alice";
        entity.tenancyId = "tenant-1";
        entity.publicKeyCose = new byte[]{5, 6, 7};
        entity.createdAt = Instant.now();

        WebAuthnCredential result = entity.toRecord();
        entity.publicKeyCose[0] = 99;

        assertThat(result.publicKeyCose()[0]).isEqualTo((byte) 5);
    }

    @Test
    void emptyTransports_serializesAsNull() {
        var credential = new WebAuthnCredential(
                "cred-5", "alice", "tenant-1",
                null, 0L, Set.of(), null, null,
                Instant.now(), null, false
        );

        WebAuthnCredentialEntity entity = WebAuthnCredentialEntity.fromRecord(credential);
        assertThat(entity.transportsJson).isNull();
    }
}
