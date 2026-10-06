package io.casehub.platform.authn.webauthn;

import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.fasterxml.jackson.dataformat.cbor.CBORGenerator;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.security.MessageDigest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthenticatorDataParserTest {

    @Test
    void parsesMinimalAuthenticatorData() throws Exception {
        byte[] rpIdHash = sha256("example.com");
        byte flags = AuthenticatorData.FLAG_UP;
        long signCount = 42;

        byte[] authData = buildAuthData(rpIdHash, flags, signCount, null, null, null);
        var parsed = AuthenticatorDataParser.parse(authData);

        assertThat(parsed.rpIdHash()).isEqualTo(rpIdHash);
        assertThat(parsed.flags()).isEqualTo(flags);
        assertThat(parsed.signCount()).isEqualTo(42);
        assertThat(parsed.userPresent()).isTrue();
        assertThat(parsed.userVerified()).isFalse();
        assertThat(parsed.hasAttestedCredentialData()).isFalse();
        assertThat(parsed.attestedCredentialData()).isNull();
    }

    @Test
    void parsesWithUserVerifiedFlag() throws Exception {
        byte[] rpIdHash = sha256("example.com");
        byte flags = (byte) (AuthenticatorData.FLAG_UP | AuthenticatorData.FLAG_UV);

        byte[] authData = buildAuthData(rpIdHash, flags, 0, null, null, null);
        var parsed = AuthenticatorDataParser.parse(authData);

        assertThat(parsed.userPresent()).isTrue();
        assertThat(parsed.userVerified()).isTrue();
    }

    @Test
    void parsesWithAttestedCredentialData() throws Exception {
        byte[] rpIdHash = sha256("example.com");
        byte flags = (byte) (AuthenticatorData.FLAG_UP | AuthenticatorData.FLAG_AT);
        byte[] aaguid = new byte[16];
        aaguid[0] = 0x01;
        byte[] credentialId = "cred-id-123".getBytes();
        byte[] coseKey = buildMinimalCoseKey();

        byte[] authData = buildAuthData(rpIdHash, flags, 5, aaguid, credentialId, coseKey);
        var parsed = AuthenticatorDataParser.parse(authData);

        assertThat(parsed.hasAttestedCredentialData()).isTrue();
        var attested = parsed.attestedCredentialData();
        assertThat(attested).isNotNull();
        assertThat(attested.aaguid()).isEqualTo(aaguid);
        assertThat(attested.credentialId()).isEqualTo(credentialId);
        assertThat(attested.credentialPublicKeyCose()).isEqualTo(coseKey);
    }

    @Test
    void parsesLargeSignCount() throws Exception {
        byte[] rpIdHash = sha256("example.com");
        long signCount = 0xFFFFFFFFL;

        byte[] authData = buildAuthData(rpIdHash, AuthenticatorData.FLAG_UP, signCount, null, null, null);
        var parsed = AuthenticatorDataParser.parse(authData);

        assertThat(parsed.signCount()).isEqualTo(0xFFFFFFFFL);
    }

    @Test
    void rejectsTooShortInput() {
        assertThatThrownBy(() -> AuthenticatorDataParser.parse(new byte[36]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("too short");
    }

    @Test
    void rejectsNullInput() {
        assertThatThrownBy(() -> AuthenticatorDataParser.parse(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsTruncatedAttestedCredentialData() throws Exception {
        byte[] rpIdHash = sha256("example.com");
        byte flags = (byte) (AuthenticatorData.FLAG_UP | AuthenticatorData.FLAG_AT);

        // AT flag set but no attested data after the fixed 37 bytes
        byte[] authData = buildAuthData(rpIdHash, flags, 0, null, null, null);
        // This is only 37 bytes, but AT flag demands more
        assertThatThrownBy(() -> AuthenticatorDataParser.parse(authData))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static byte[] buildAuthData(byte[] rpIdHash, byte flags, long signCount,
                                         byte[] aaguid, byte[] credentialId, byte[] coseKey) {
        int size = 37;
        if (aaguid != null) {
            size += 16 + 2 + credentialId.length + coseKey.length;
        }
        var buf = ByteBuffer.allocate(size);
        buf.put(rpIdHash);
        buf.put(flags);
        buf.putInt((int) signCount);
        if (aaguid != null) {
            buf.put(aaguid);
            buf.putShort((short) credentialId.length);
            buf.put(credentialId);
            buf.put(coseKey);
        }
        return buf.array();
    }

    static byte[] buildMinimalCoseKey() throws Exception {
        var bos = new ByteArrayOutputStream();
        var factory = new CBORFactory();
        try (var gen = factory.createGenerator(bos)) {
            gen.writeStartObject();
            gen.writeFieldId(1);  // kty
            gen.writeNumber(2);   // EC2
            gen.writeFieldId(3);  // alg
            gen.writeNumber(-7);  // ES256
            gen.writeFieldId(-1); // crv
            gen.writeNumber(1);   // P-256
            gen.writeFieldId(-2); // x
            gen.writeBinary(new byte[32]);
            gen.writeFieldId(-3); // y
            gen.writeBinary(new byte[32]);
            gen.writeEndObject();
        }
        return bos.toByteArray();
    }

    static byte[] sha256(String input) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(input.getBytes());
    }
}
