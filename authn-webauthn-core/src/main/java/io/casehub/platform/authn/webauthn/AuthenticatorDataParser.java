package io.casehub.platform.authn.webauthn;

import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.fasterxml.jackson.dataformat.cbor.CBORParser;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;

public final class AuthenticatorDataParser {

    private static final int MIN_LENGTH = 37; // 32 rpIdHash + 1 flags + 4 signCount
    private static final int AAGUID_LENGTH = 16;
    private static final CBORFactory CBOR_FACTORY = new CBORFactory();

    private AuthenticatorDataParser() {}

    public static AuthenticatorData parse(byte[] authData) {
        Objects.requireNonNull(authData, "authData");
        if (authData.length < MIN_LENGTH) {
            throw new IllegalArgumentException(
                    "Authenticator data too short: " + authData.length + " bytes, minimum " + MIN_LENGTH);
        }

        var buf = ByteBuffer.wrap(authData);
        byte[] rpIdHash = new byte[32];
        buf.get(rpIdHash);

        byte flags = buf.get();
        long signCount = Integer.toUnsignedLong(buf.getInt());

        AttestedCredentialData attestedCredentialData = null;
        if ((flags & AuthenticatorData.FLAG_AT) != 0) {
            attestedCredentialData = parseAttestedCredentialData(buf);
        }

        return new AuthenticatorData(rpIdHash, flags, signCount, attestedCredentialData);
    }

    private static AttestedCredentialData parseAttestedCredentialData(ByteBuffer buf) {
        if (buf.remaining() < AAGUID_LENGTH + 2) {
            throw new IllegalArgumentException(
                    "Attested credential data truncated: " + buf.remaining() + " bytes remaining");
        }

        byte[] aaguid = new byte[AAGUID_LENGTH];
        buf.get(aaguid);

        int credentialIdLength = Short.toUnsignedInt(buf.getShort());
        if (buf.remaining() < credentialIdLength) {
            throw new IllegalArgumentException(
                    "Credential ID truncated: need " + credentialIdLength + " bytes, have " + buf.remaining());
        }
        byte[] credentialId = new byte[credentialIdLength];
        buf.get(credentialId);

        byte[] coseKeyBytes = extractCborObject(buf);
        return new AttestedCredentialData(aaguid, credentialId, coseKeyBytes);
    }

    private static byte[] extractCborObject(ByteBuffer buf) {
        if (!buf.hasRemaining()) {
            throw new IllegalArgumentException("No COSE key data after credential ID");
        }
        int startPos = buf.position();
        byte[] remaining = new byte[buf.remaining()];
        buf.get(remaining);

        try (CBORParser parser = CBOR_FACTORY.createParser(remaining)) {
            parser.nextToken(); // START_OBJECT
            parser.skipChildren();
            int consumed = (int) parser.currentLocation().getByteOffset();
            byte[] coseKey = new byte[consumed];
            System.arraycopy(remaining, 0, coseKey, 0, consumed);
            return coseKey;
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid CBOR COSE key in attested credential data", e);
        }
    }
}
