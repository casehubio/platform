package io.casehub.platform.signing.document;

import eu.europa.esig.dss.cades.CAdESSignatureParameters;
import eu.europa.esig.dss.cades.signature.CAdESService;
import eu.europa.esig.dss.enumerations.DigestAlgorithm;
import eu.europa.esig.dss.enumerations.SignatureLevel;
import eu.europa.esig.dss.enumerations.SignaturePackaging;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.InMemoryDocument;
import eu.europa.esig.dss.pades.PAdESSignatureParameters;
import eu.europa.esig.dss.pades.signature.PAdESService;
import eu.europa.esig.dss.service.tsp.OnlineTSPSource;
import eu.europa.esig.dss.spi.validation.CommonCertificateVerifier;
import eu.europa.esig.dss.token.DSSPrivateKeyEntry;
import io.casehub.platform.api.signing.document.DetachedSignature;
import io.casehub.platform.api.signing.document.DocumentSigningService;
import io.casehub.platform.api.signing.document.SignedDocument;
import io.casehub.platform.api.signing.document.SigningIdentity;
import io.casehub.platform.api.signing.document.SigningProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.Optional;

@ApplicationScoped
public class DssDocumentSigningService implements DocumentSigningService {

    private final KeyStoreManager keyStoreManager;
    private final SigningProfile profile;
    private final String tsaUrl;

    @Inject
    DssDocumentSigningService(DssSigningConfig config) {
        this(new KeyStoreManager(
                        config.keystorePath().orElse(null),
                        config.keystorePassword().orElse(null),
                        config.keystoreType(),
                        config.keyAlias().orElse(null)),
                config.padesProfile(),
                config.tsaUrl().orElse(null));
    }

    DssDocumentSigningService(KeyStoreManager keyStoreManager, SigningProfile profile,
                              String tsaUrl) {
        this.keyStoreManager = keyStoreManager;
        this.profile = profile;
        this.tsaUrl = tsaUrl;
    }

    @Override
    public Optional<SignedDocument> signPdf(byte[] pdfBytes, SigningIdentity identity) {
        if (!keyStoreManager.isLoaded()) return Optional.empty();
        validateTsaAvailability();

        DSSPrivateKeyEntry key = keyStoreManager.resolveKey(identity.tenancyId());
        var params = new PAdESSignatureParameters();
        params.setSignatureLevel(toPadesLevel(profile));
        params.setDigestAlgorithm(DigestAlgorithm.SHA256);
        params.setSigningCertificate(key.getCertificate());
        params.setCertificateChain(key.getCertificateChain());

        var verifier = new CommonCertificateVerifier();
        var service = new PAdESService(verifier);
        if (profile.requiresTimestamp()) {
            service.setTspSource(new OnlineTSPSource(tsaUrl));
        }

        DSSDocument doc = new InMemoryDocument(pdfBytes);
        var dataToSign = service.getDataToSign(doc, params);
        var signatureValue = keyStoreManager.getToken().sign(
                dataToSign, DigestAlgorithm.SHA256, key);
        DSSDocument signed = service.signDocument(doc, params, signatureValue);

        String signerDn = key.getCertificate().getSubject().getPrincipal().getName();
        return Optional.of(new SignedDocument(
                toBytes(signed), signerDn, Instant.now(),
                key.getCertificate().getDSSIdAsString(), profile));
    }

    @Override
    public Optional<DetachedSignature> signDetached(byte[] data, SigningIdentity identity) {
        if (!keyStoreManager.isLoaded()) return Optional.empty();
        validateTsaAvailability();

        DSSPrivateKeyEntry key = keyStoreManager.resolveKey(identity.tenancyId());
        var params = new CAdESSignatureParameters();
        params.setSignatureLevel(toCadesLevel(profile));
        params.setSignaturePackaging(SignaturePackaging.DETACHED);
        params.setDigestAlgorithm(DigestAlgorithm.SHA256);
        params.setSigningCertificate(key.getCertificate());
        params.setCertificateChain(key.getCertificateChain());

        var verifier = new CommonCertificateVerifier();
        var service = new CAdESService(verifier);
        if (profile.requiresTimestamp()) {
            service.setTspSource(new OnlineTSPSource(tsaUrl));
        }

        DSSDocument doc = new InMemoryDocument(data);
        var dataToSign = service.getDataToSign(doc, params);
        var signatureValue = keyStoreManager.getToken().sign(
                dataToSign, DigestAlgorithm.SHA256, key);
        DSSDocument signed = service.signDocument(doc, params, signatureValue);

        String signerDn = key.getCertificate().getSubject().getPrincipal().getName();
        return Optional.of(new DetachedSignature(
                toBytes(signed), signerDn, Instant.now(),
                key.getCertificate().getDSSIdAsString(), profile));
    }

    private void validateTsaAvailability() {
        if (profile.requiresTimestamp() && (tsaUrl == null || tsaUrl.isBlank())) {
            throw new IllegalStateException(
                    "Profile " + profile + " requires timestamping but TSA URL is not configured. " +
                    "Configure casehub.signing.tsa-url or use B_B profile.");
        }
    }

    private static SignatureLevel toPadesLevel(SigningProfile profile) {
        return switch (profile) {
            case B_B -> SignatureLevel.PAdES_BASELINE_B;
            case B_T -> SignatureLevel.PAdES_BASELINE_T;
            case B_LT -> SignatureLevel.PAdES_BASELINE_LT;
            case B_LTA -> SignatureLevel.PAdES_BASELINE_LTA;
        };
    }

    private static SignatureLevel toCadesLevel(SigningProfile profile) {
        return switch (profile) {
            case B_B -> SignatureLevel.CAdES_BASELINE_B;
            case B_T -> SignatureLevel.CAdES_BASELINE_T;
            case B_LT, B_LTA -> SignatureLevel.CAdES_BASELINE_T;
        };
    }

    private static byte[] toBytes(DSSDocument doc) {
        try (var os = new java.io.ByteArrayOutputStream()) {
            doc.writeTo(os);
            return os.toByteArray();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Failed to read signed document bytes", e);
        }
    }
}
