package io.casehub.platform.pdf;

import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import io.casehub.platform.api.pdf.PdfGenerator;
import io.casehub.platform.api.pdf.PdfOptions;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;

import java.io.ByteArrayOutputStream;
import java.util.Calendar;
import java.util.List;
import java.util.Optional;
import java.util.TimeZone;

@ApplicationScoped
public class OpenHtmlToPdfGenerator implements PdfGenerator {

    private static final List<FontDef> FONTS = List.of(
            new FontDef("LiberationSans-Regular.ttf", "Liberation Sans", 400, BaseRendererBuilder.FontStyle.NORMAL),
            new FontDef("LiberationSans-Bold.ttf", "Liberation Sans", 700, BaseRendererBuilder.FontStyle.NORMAL),
            new FontDef("LiberationSans-Italic.ttf", "Liberation Sans", 400, BaseRendererBuilder.FontStyle.ITALIC),
            new FontDef("LiberationSans-BoldItalic.ttf", "Liberation Sans", 700, BaseRendererBuilder.FontStyle.ITALIC),
            new FontDef("LiberationMono-Regular.ttf", "Liberation Mono", 400, BaseRendererBuilder.FontStyle.NORMAL),
            new FontDef("LiberationMono-Bold.ttf", "Liberation Mono", 700, BaseRendererBuilder.FontStyle.NORMAL));

    @Override
    public Optional<byte[]> generateFromHtml(String html, PdfOptions options) {
        try {
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.usePdfAConformance(PdfRendererBuilder.PdfAConformance.PDFA_2_B);
            builder.withHtmlContent(html, "/");
            builder.toStream(os);

            for (FontDef font : FONTS) {
                builder.useFont(() -> OpenHtmlToPdfGenerator.class.getResourceAsStream("/fonts/" + font.file()),
                        font.family(), font.weight(), font.style(), true);
            }

            builder.run();
            return Optional.of(setMetadata(os.toByteArray(), options));
        } catch (Exception e) {
            throw new IllegalStateException("PDF generation failed", e);
        }
    }

    private byte[] setMetadata(byte[] pdfBytes, PdfOptions options) {
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            PDDocumentInformation info = doc.getDocumentInformation();
            if (info == null) info = new PDDocumentInformation();
            if (options.title() != null) info.setTitle(options.title());
            if (options.author() != null) info.setAuthor(options.author());
            if (options.reportType() != null) info.setSubject(options.reportType());
            if (options.createdAt() != null) {
                Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
                cal.setTimeInMillis(options.createdAt().toEpochMilli());
                info.setCreationDate(cal);
            }
            doc.setDocumentInformation(info);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to set PDF metadata", e);
        }
    }

    private record FontDef(String file, String family, int weight, BaseRendererBuilder.FontStyle style) {}
}
