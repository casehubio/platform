package io.casehub.platform.signing.document;

import io.casehub.platform.api.signing.document.DetachedSignature;
import io.casehub.platform.api.signing.document.DocumentSigningService;
import io.casehub.platform.api.signing.document.SignedDocument;
import io.casehub.platform.api.signing.document.SigningIdentity;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;

@DefaultBean
@ApplicationScoped
public class NoOpDocumentSigningService implements DocumentSigningService {

    @Override
    public Optional<SignedDocument> signPdf(byte[] pdfBytes, SigningIdentity identity) {
        return Optional.empty();
    }

    @Override
    public Optional<DetachedSignature> signDetached(byte[] data, SigningIdentity identity) {
        return Optional.empty();
    }
}
