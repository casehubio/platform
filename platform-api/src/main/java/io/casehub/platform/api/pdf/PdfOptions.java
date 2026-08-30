package io.casehub.platform.api.pdf;

import java.time.Instant;

public record PdfOptions(
        String title,
        String author,
        Instant createdAt,
        String reportType,
        PdfAConformance conformance) {

    public static PdfOptions defaults() {
        return new PdfOptions(null, null, null, null, PdfAConformance.PDFA_2_B);
    }
}
