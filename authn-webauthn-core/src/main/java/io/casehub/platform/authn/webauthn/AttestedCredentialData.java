package io.casehub.platform.authn.webauthn;

import java.util.Arrays;
import java.util.Objects;

public record AttestedCredentialData(
    byte[] aaguid,
    byte[] credentialId,
    byte[] credentialPublicKeyCose
) {
    public AttestedCredentialData {
        Objects.requireNonNull(aaguid, "aaguid");
        Objects.requireNonNull(credentialId, "credentialId");
        Objects.requireNonNull(credentialPublicKeyCose, "credentialPublicKeyCose");
        aaguid = Arrays.copyOf(aaguid, aaguid.length);
        credentialId = Arrays.copyOf(credentialId, credentialId.length);
        credentialPublicKeyCose = Arrays.copyOf(credentialPublicKeyCose, credentialPublicKeyCose.length);
    }

    @Override
    public byte[] aaguid() {
        return Arrays.copyOf(aaguid, aaguid.length);
    }

    @Override
    public byte[] credentialId() {
        return Arrays.copyOf(credentialId, credentialId.length);
    }

    @Override
    public byte[] credentialPublicKeyCose() {
        return Arrays.copyOf(credentialPublicKeyCose, credentialPublicKeyCose.length);
    }
}
