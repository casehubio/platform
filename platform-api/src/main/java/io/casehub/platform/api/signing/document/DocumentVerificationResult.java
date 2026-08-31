package io.casehub.platform.api.signing.document;

import java.time.Instant;
import java.util.List;

public record DocumentVerificationResult(
        VerificationStatus status, String signerDn, Instant signedAt,
        String keyRef, SigningProfile detectedProfile,
        List<CertificateInfo> certificateChain, String diagnosticMessage) {

    public static DocumentVerificationResult unsigned() {
        return new DocumentVerificationResult(
                VerificationStatus.UNSIGNED, null, null, null, null, List.of(), null);
    }
}
