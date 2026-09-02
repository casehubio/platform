package io.casehub.platform.signing.document;

import eu.europa.esig.dss.enumerations.Indication;
import eu.europa.esig.dss.model.InMemoryDocument;
import eu.europa.esig.dss.spi.validation.CommonCertificateVerifier;
import eu.europa.esig.dss.validation.SignedDocumentValidator;
import io.casehub.platform.api.signing.document.CertificateInfo;
import io.casehub.platform.api.signing.document.DocumentVerificationResult;
import io.casehub.platform.api.signing.document.DocumentVerificationService;
import io.casehub.platform.api.signing.document.VerificationStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.List;

@ApplicationScoped
public class DssDocumentVerificationService implements DocumentVerificationService {

    private static final Logger LOG = Logger.getLogger(DssDocumentVerificationService.class);

    private final TrustedListManager trustedListManager;

    @Inject
    DssDocumentVerificationService(TrustedListManager trustedListManager) {
        this.trustedListManager = trustedListManager;
    }

    public DssDocumentVerificationService() {
        this.trustedListManager = null;
    }

    @Override
    public DocumentVerificationResult verifyPdf(byte[] pdfBytes) {
        return verify(new InMemoryDocument(pdfBytes), null);
    }

    @Override
    public DocumentVerificationResult verifyDetached(byte[] data, byte[] signature) {
        return verify(new InMemoryDocument(signature),
                List.of(new InMemoryDocument(data)));
    }

    private DocumentVerificationResult verify(InMemoryDocument signatureDoc,
                                               List<InMemoryDocument> detachedContents) {
        try {
            var validator = SignedDocumentValidator.fromDocument(signatureDoc);
            var verifier = new CommonCertificateVerifier();
            if (trustedListManager != null && trustedListManager.isEnabled()) {
                verifier.addTrustedCertSources(trustedListManager.getTrustedListSource());
            }
            validator.setCertificateVerifier(verifier);
            if (detachedContents != null) {
                validator.setDetachedContents(List.copyOf(detachedContents));
            }

            var signatures = validator.getSignatures();
            if (signatures.isEmpty()) {
                return DocumentVerificationResult.unsigned();
            }

            var reports = validator.validateDocument();
            var simpleReport = reports.getSimpleReport();
            var sigId = simpleReport.getSignatureIdList().getFirst();
            var indication = simpleReport.getIndication(sigId);

            VerificationStatus status;
            if (indication == Indication.TOTAL_PASSED || indication == Indication.PASSED) {
                status = VerificationStatus.VALID;
            } else if (indication == Indication.TOTAL_FAILED || indication == Indication.FAILED) {
                status = VerificationStatus.INVALID;
            } else {
                status = VerificationStatus.VALID;
            }

            var sig = signatures.getFirst();
            String signerDn = sig.getSigningCertificateToken() != null
                    ? sig.getSigningCertificateToken().getSubject().getPrincipal().getName()
                    : null;
            Instant signedAt = sig.getSigningTime() != null
                    ? sig.getSigningTime().toInstant()
                    : null;

            List<CertificateInfo> chain = sig.getCertificates().stream()
                    .map(cert -> new CertificateInfo(
                            cert.getSubject().getPrincipal().getName(),
                            cert.getIssuer().getPrincipal().getName(),
                            cert.getNotBefore().toInstant(),
                            cert.getNotAfter().toInstant(),
                            false))
                    .toList();

            return new DocumentVerificationResult(
                    status, signerDn, signedAt,
                    sig.getSigningCertificateToken() != null
                            ? sig.getSigningCertificateToken().getDSSIdAsString() : null,
                    null, chain, null);

        } catch (Exception e) {
            LOG.warn("Verification failed", e);
            return new DocumentVerificationResult(
                    VerificationStatus.ERROR, null, null, null, null, List.of(),
                    e.getMessage());
        }
    }
}
