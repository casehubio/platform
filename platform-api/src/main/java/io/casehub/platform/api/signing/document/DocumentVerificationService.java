package io.casehub.platform.api.signing.document;

public interface DocumentVerificationService {
    DocumentVerificationResult verifyPdf(byte[] pdfBytes);
    DocumentVerificationResult verifyDetached(byte[] data, byte[] signature);
}
