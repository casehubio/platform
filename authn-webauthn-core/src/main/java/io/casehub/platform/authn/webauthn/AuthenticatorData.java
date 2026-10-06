package io.casehub.platform.authn.webauthn;

import java.util.Arrays;
import java.util.Objects;

public record AuthenticatorData(
    byte[] rpIdHash,
    byte flags,
    long signCount,
    AttestedCredentialData attestedCredentialData
) {
    public static final byte FLAG_UP = 0x01;
    public static final byte FLAG_UV = 0x04;
    public static final byte FLAG_AT = 0x40;
    public static final byte FLAG_ED = (byte) 0x80;

    public AuthenticatorData {
        Objects.requireNonNull(rpIdHash, "rpIdHash");
        rpIdHash = Arrays.copyOf(rpIdHash, rpIdHash.length);
    }

    @Override
    public byte[] rpIdHash() {
        return Arrays.copyOf(rpIdHash, rpIdHash.length);
    }

    public boolean userPresent() {
        return (flags & FLAG_UP) != 0;
    }

    public boolean userVerified() {
        return (flags & FLAG_UV) != 0;
    }

    public boolean hasAttestedCredentialData() {
        return (flags & FLAG_AT) != 0;
    }

    public boolean hasExtensions() {
        return (flags & FLAG_ED) != 0;
    }
}
