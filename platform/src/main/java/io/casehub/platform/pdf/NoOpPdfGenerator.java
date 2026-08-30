package io.casehub.platform.pdf;

import io.casehub.platform.api.pdf.PdfGenerator;
import io.casehub.platform.api.pdf.PdfOptions;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;

@DefaultBean
@ApplicationScoped
public class NoOpPdfGenerator implements PdfGenerator {

    @Override
    public Optional<byte[]> generateFromHtml(String html, PdfOptions options) {
        return Optional.empty();
    }
}
