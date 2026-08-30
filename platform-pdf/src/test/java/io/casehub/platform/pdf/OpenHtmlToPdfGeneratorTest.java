package io.casehub.platform.pdf;

import io.casehub.platform.api.pdf.PdfAConformance;
import io.casehub.platform.api.pdf.PdfOptions;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class OpenHtmlToPdfGeneratorTest {

    final OpenHtmlToPdfGenerator generator = new OpenHtmlToPdfGenerator();

    @Test
    void generateFromHtml_producesPdfBytes() {
        String html = "<!DOCTYPE html><html><head><style>"
                + "body { font-family: 'Liberation Sans', sans-serif; }"
                + "</style></head><body><h1>Test</h1></body></html>";

        var result = generator.generateFromHtml(html, PdfOptions.defaults());

        assertThat(result).isPresent();
        byte[] pdf = result.get();
        assertThat(pdf[0]).isEqualTo((byte) '%');
        assertThat(pdf[1]).isEqualTo((byte) 'P');
        assertThat(pdf[2]).isEqualTo((byte) 'D');
        assertThat(pdf[3]).isEqualTo((byte) 'F');
    }

    @Test
    void generateFromHtml_setsDocumentMetadata() throws IOException {
        String html = "<!DOCTYPE html><html><head></head><body>Content</body></html>";
        var options = new PdfOptions("Test Title", "Test Author",
                Instant.parse("2026-08-30T12:00:00Z"), "OBLIGATION",
                PdfAConformance.PDFA_2_B);

        byte[] pdf = generator.generateFromHtml(html, options).orElseThrow();

        try (PDDocument doc = Loader.loadPDF(pdf)) {
            var info = doc.getDocumentInformation();
            assertThat(info.getTitle()).isEqualTo("Test Title");
            assertThat(info.getAuthor()).isEqualTo("Test Author");
            assertThat(info.getSubject()).isEqualTo("OBLIGATION");
        }
    }

    @Test
    void generateFromHtml_producesMultiplePages() throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><style>");
        sb.append("body { font-family: 'Liberation Sans', sans-serif; }");
        sb.append("</style></head><body>");
        for (int i = 0; i < 100; i++) {
            sb.append("<p>Paragraph ").append(i).append(" with enough text.</p>");
        }
        sb.append("</body></html>");

        byte[] pdf = generator.generateFromHtml(sb.toString(), PdfOptions.defaults()).orElseThrow();

        try (PDDocument doc = Loader.loadPDF(pdf)) {
            assertThat(doc.getPages().getCount()).isGreaterThan(1);
        }
    }
}
