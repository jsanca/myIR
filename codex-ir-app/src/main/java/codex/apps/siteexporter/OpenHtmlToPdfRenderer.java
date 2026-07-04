package codex.apps.siteexporter;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Renders HTML to PDF using OpenHTMLToPDF backed by Apache PDFBox.
 *
 * <p>The HTML file is read as a string; the base URI for resolving relative
 * CSS and image references is derived from the file's parent directory unless
 * overridden via {@link PdfRenderOptions#baseUri()}.</p>
 *
 * <p>OpenHTMLToPDF runs synchronously and is not thread-safe per builder
 * instance. Callers that need concurrent rendering must create one
 * {@code OpenHtmlToPdfRenderer} per thread or synchronize externally.</p>
 */
public final class OpenHtmlToPdfRenderer implements PdfRenderer {

    @Override
    public RenderedPdf render(final Path htmlFile, final PdfRenderOptions options) throws IOException {
        Objects.requireNonNull(htmlFile, "htmlFile");
        Objects.requireNonNull(options, "options");

        final String html = Files.readString(htmlFile);
        final String baseUri = options.baseUri() != null
                ? options.baseUri()
                : htmlFile.toAbsolutePath().getParent().toUri().toString();

        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            final PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, baseUri);
            builder.toStream(out);
            builder.run();
        } catch (final IOException e) {
            throw e;
        } catch (final Exception e) {
            throw new IOException("PDF rendering failed for: " + htmlFile, e);
        }

        return new RenderedPdf(out.toByteArray(), htmlFile);
    }
}
