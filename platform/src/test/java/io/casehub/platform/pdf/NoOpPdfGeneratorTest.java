package io.casehub.platform.pdf;

import io.casehub.platform.api.pdf.PdfAConformance;
import io.casehub.platform.api.pdf.PdfOptions;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NoOpPdfGeneratorTest {

    @Test
    void generateFromHtml_returnsEmpty() {
        var noOp = new NoOpPdfGenerator();
        var result = noOp.generateFromHtml("<html></html>", PdfOptions.defaults());
        assertThat(result).isEmpty();
    }

    @Test
    void pdfOptionsDefaults_hasPdfA2b() {
        var opts = PdfOptions.defaults();
        assertThat(opts.conformance()).isEqualTo(PdfAConformance.PDFA_2_B);
        assertThat(opts.title()).isNull();
        assertThat(opts.author()).isNull();
        assertThat(opts.createdAt()).isNull();
        assertThat(opts.reportType()).isNull();
    }
}
