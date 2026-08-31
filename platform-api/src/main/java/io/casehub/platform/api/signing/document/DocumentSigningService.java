package io.casehub.platform.api.signing.document;

import java.util.Optional;

public interface DocumentSigningService {
    Optional<SignedDocument> signPdf(byte[] pdfBytes, SigningIdentity identity);
    Optional<DetachedSignature> signDetached(byte[] data, SigningIdentity identity);
}
