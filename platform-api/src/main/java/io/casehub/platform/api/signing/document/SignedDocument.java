package io.casehub.platform.api.signing.document;

import java.time.Instant;
import java.util.Arrays;

public record SignedDocument(byte[] signedBytes, String signerDn, Instant signedAt,
                             String keyRef, SigningProfile profile) {
    public SignedDocument {
        signedBytes = Arrays.copyOf(signedBytes, signedBytes.length);
    }

    @Override
    public byte[] signedBytes() {
        return Arrays.copyOf(signedBytes, signedBytes.length);
    }
}
