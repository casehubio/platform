package io.casehub.platform.api.signing.document;

import java.time.Instant;
import java.util.Arrays;

public record DetachedSignature(byte[] signatureBytes, String signerDn, Instant signedAt,
                                 String keyRef, SigningProfile profile) {
    public DetachedSignature {
        signatureBytes = Arrays.copyOf(signatureBytes, signatureBytes.length);
    }

    @Override
    public byte[] signatureBytes() {
        return Arrays.copyOf(signatureBytes, signatureBytes.length);
    }
}
