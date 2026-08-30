package io.casehub.platform.api.pdf;

import java.util.Optional;

public interface PdfGenerator {
    Optional<byte[]> generateFromHtml(String html, PdfOptions options);
}
