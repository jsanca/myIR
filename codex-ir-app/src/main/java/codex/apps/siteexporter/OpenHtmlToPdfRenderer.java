package codex.apps.siteexporter;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Renders HTML to PDF using OpenHTMLToPDF backed by Apache PDFBox.
 *
 * <p>Raw HTML is first converted to print-friendly XHTML by
 * {@link HtmlToXhtmlSanitizer#sanitizeForPrint} before being passed to OpenHTMLToPDF.
 * This removes scripts, strips inlined base64 font declarations, and fixes void-element
 * and unclosed-tag issues common in mirrored real-world pages.</p>
 *
 * <p>The base URI for resolving relative CSS and image references is derived from the
 * file's parent directory unless overridden via {@link PdfRenderOptions#baseUri()}.</p>
 *
 * <p>When constructed with a non-{@code null} {@code debugDir}, the sanitized XHTML
 * is written to {@code <debugDir>/<htmlFileName>.xhtml} on any render failure, so the
 * exact input that triggered the error can be inspected offline.</p>
 *
 * <p>OpenHTMLToPDF runs synchronously and is not thread-safe per builder instance.
 * Callers that need concurrent rendering must create one {@code OpenHtmlToPdfRenderer}
 * per thread or synchronize externally.</p>
 */
public final class OpenHtmlToPdfRenderer implements PdfRenderer {

    private final HtmlToXhtmlSanitizer sanitizer;
    /** Null means debug output is disabled. */
    private final Path debugDir;

    public OpenHtmlToPdfRenderer() {
        this(new HtmlToXhtmlSanitizer(), null);
    }

    /**
     * Creates a renderer that writes sanitized XHTML to {@code debugDir} on failure.
     * The directory is created automatically if it does not exist.
     *
     * @param debugDir directory for debug XHTML files; {@code null} disables debug output
     */
    public OpenHtmlToPdfRenderer(final Path debugDir) {
        this(new HtmlToXhtmlSanitizer(), debugDir);
    }

    /** Package-private for test injection of a custom sanitizer. */
    OpenHtmlToPdfRenderer(final HtmlToXhtmlSanitizer sanitizer, final Path debugDir) {
        this.sanitizer = Objects.requireNonNull(sanitizer, "sanitizer");
        this.debugDir = debugDir;
    }

    @Override
    public RenderedPdf render(final Path htmlFile, final PdfRenderOptions options) throws IOException {
        Objects.requireNonNull(htmlFile, "htmlFile");
        Objects.requireNonNull(options, "options");

        final String rawHtml = Files.readString(htmlFile);
        final String baseUri = options.baseUri() != null
                ? options.baseUri()
                : htmlFile.toAbsolutePath().getParent().toUri().toString();

        final String xhtml = sanitizer.sanitizeForPrint(rawHtml, baseUri);

        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            final PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(xhtml, baseUri);
            builder.toStream(out);
            builder.run();
        } catch (final IOException e) {
            writeDebugXhtmlQuietly(htmlFile, xhtml);
            throw e;
        } catch (final Exception e) {
            writeDebugXhtmlQuietly(htmlFile, xhtml);
            final String cause = e.getMessage() != null ? ": " + e.getMessage() : "";
            throw new IOException("PDF rendering failed for: " + htmlFile + cause, e);
        }

        return new RenderedPdf(out.toByteArray(), htmlFile);
    }

    private void writeDebugXhtmlQuietly(final Path htmlFile, final String xhtml) {
        if (debugDir == null) {
            return;
        }
        try {
            Files.createDirectories(debugDir);
            final Path debugFile = debugDir.resolve(htmlFile.getFileName() + ".xhtml");
            Files.writeString(debugFile, xhtml, StandardCharsets.UTF_8);
        } catch (final IOException ignored) {
            // Debug write must never suppress the original render failure.
        }
    }
}
