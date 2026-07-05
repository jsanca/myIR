package codex.apps.siteexporter;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * {@link PublicationDriver} that renders each mirrored page to PDF and assembles
 * them into one combined PDF document.
 *
 * <p>Wiring:</p>
 * <ul>
 *   <li>{@link Pdf2HtmlExAwarePdfRenderer} wraps {@link OpenHtmlToPdfRenderer} to
 *       transparently handle pdf2htmlEX pages via the reader-extraction path.</li>
 *   <li>{@link ManifestOrderPdfAssemblyStrategy} merges per-page PDFs.</li>
 *   <li>{@link PublicationPipeline} sequences rendering and assembly.</li>
 * </ul>
 *
 * <p>Reader HTML side-output files are written to {@code outputDir/reader-pages/}.</p>
 */
public final class PdfPublicationDriver implements PublicationDriver {

    private final Path outputDir;

    /**
     * @param outputDir mirror output directory; {@code reader-pages/} is created here;
     *                  must not be {@code null}
     */
    public PdfPublicationDriver(final Path outputDir) {
        this.outputDir = Objects.requireNonNull(outputDir, "outputDir");
    }

    @Override
    public boolean requiresAssetProcessing() {
        return true;
    }

    @Override
    public PublicationArtifact publish(final PublicationSource source,
            final PublicationExportOptions options) throws IOException {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(options, "options");

        final Path readerPagesDir = outputDir.resolve("reader-pages");
        final PdfRenderer renderer = new Pdf2HtmlExAwarePdfRenderer(
                new OpenHtmlToPdfRenderer(), readerPagesDir);

        return PublicationPipeline.builder()
                .source(source)
                .renderer(renderer)
                .assemblyStrategy(new ManifestOrderPdfAssemblyStrategy())
                .output(options.outputPath())
                .format(options.format())
                .build()
                .run();
    }
}
