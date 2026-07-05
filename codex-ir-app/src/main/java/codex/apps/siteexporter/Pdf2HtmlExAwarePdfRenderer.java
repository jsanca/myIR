package codex.apps.siteexporter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * A {@link PdfRenderer} decorator that transparently routes pdf2htmlEX pages through
 * the reader-extraction pipeline before rendering.
 *
 * <p>For each HTML file presented to {@link #render}:</p>
 * <ol>
 *   <li>The raw HTML is read once and tested by {@link Pdf2HtmlExDetector}.</li>
 *   <li><strong>pdf2htmlEX page:</strong> text is extracted via
 *       {@link Pdf2HtmlExTextExtractor}, a clean reader HTML is written to
 *       {@code readerPagesDir/<fileName>.reader.html}, and the inner renderer is
 *       invoked on that reader file. The reader directory is auto-created if absent.</li>
 *   <li><strong>Normal page:</strong> the inner renderer is invoked on the original
 *       HTML file unchanged.</li>
 * </ol>
 *
 * <p>Page order, assembly, and failure handling are all owned by
 * {@link PublicationPipeline} and are not affected by this decorator.</p>
 *
 * <p>Note: on the normal path the HTML file is read twice — once here for detection
 * and once inside the inner renderer. This is acceptable given that most pages in a
 * typical mirror are not pdf2htmlEX output.</p>
 */
public final class Pdf2HtmlExAwarePdfRenderer implements PdfRenderer {

    private final PdfRenderer delegate;
    private final Path readerPagesDir;
    private final Pdf2HtmlExDetector detector;
    private final Pdf2HtmlExTextExtractor extractor;
    private final ReaderHtmlWriter writer;

    /**
     * Creates a routing renderer.
     *
     * @param delegate       inner renderer used for all final rendering; must not be {@code null}
     * @param readerPagesDir directory where extracted reader HTML files are written;
     *                       created automatically if absent; must not be {@code null}
     */
    public Pdf2HtmlExAwarePdfRenderer(final PdfRenderer delegate, final Path readerPagesDir) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.readerPagesDir = Objects.requireNonNull(readerPagesDir, "readerPagesDir");
        this.detector = new Pdf2HtmlExDetector();
        this.extractor = new Pdf2HtmlExTextExtractor();
        this.writer = new ReaderHtmlWriter();
    }

    @Override
    public RenderedPdf render(final Path htmlFile, final PdfRenderOptions options)
            throws IOException {
        Objects.requireNonNull(htmlFile, "htmlFile");
        Objects.requireNonNull(options, "options");

        final String rawHtml = Files.readString(htmlFile);
        final String baseUri = options.baseUri() != null
                ? options.baseUri()
                : htmlFile.toAbsolutePath().getParent().toUri().toString();

        if (detector.detect(rawHtml, baseUri)) {
            return renderViaReaderPath(htmlFile, rawHtml, baseUri);
        }
        return delegate.render(htmlFile, options);
    }

    private RenderedPdf renderViaReaderPath(final Path htmlFile, final String rawHtml,
            final String baseUri) throws IOException {
        final ReaderDocument readerDoc = extractor.extract(rawHtml, baseUri);
        final String readerHtml = writer.write(readerDoc);

        Files.createDirectories(readerPagesDir);
        final Path readerFile = readerPagesDir.resolve(htmlFile.getFileName() + ".reader.html");
        Files.writeString(readerFile, readerHtml, StandardCharsets.UTF_8);

        System.out.printf("[Pdf2HtmlEx] %s → reader path (%d paragraphs across %d page(s)) → %s%n",
                htmlFile.getFileName(), readerDoc.totalParagraphs(),
                readerDoc.pages().size(), readerFile.getFileName());

        return delegate.render(readerFile, PdfRenderOptions.forFile(readerFile));
    }
}
