package io.casehub.platform.signing.document;

import io.casehub.platform.api.signing.document.DocumentVerificationResult;
import io.casehub.platform.api.signing.document.DocumentVerificationService;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

@DefaultBean
@ApplicationScoped
public class NoOpDocumentVerificationService implements DocumentVerificationService {

    @Override
    public DocumentVerificationResult verifyPdf(byte[] pdfBytes) {
        return DocumentVerificationResult.unsigned();
    }

    @Override
    public DocumentVerificationResult verifyDetached(byte[] data, byte[] signature) {
        return DocumentVerificationResult.unsigned();
    }
}
