package codex.apps.siteexporter;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Factory for {@link PublicationDriver} instances.
 *
 * <p>Usage:</p>
 * <pre>{@code
 * PublicationDriver driver = PublicationDrivers.forFormat(
 *         exportOptions.format(), mirrorOptions.outputDir());
 *
 * PublicationArtifact artifact = driver.publish(source, exportOptions);
 * }</pre>
 */
public final class PublicationDrivers {

    private PublicationDrivers() {}

    /**
     * Returns a {@link PublicationDriver} for the given format.
     *
     * @param format    the desired output format; must not be {@code null}
     * @param outputDir mirror output directory used by the driver for side-output
     *                  directories (e.g. {@code reader-pages/}, {@code markdown-pages/});
     *                  must not be {@code null}
     * @return the matching driver; never {@code null}
     * @throws IllegalArgumentException if {@code format} is not yet supported
     *                                  (currently {@link PublicationFormat#EPUB})
     */
    public static PublicationDriver forFormat(final PublicationFormat format,
            final Path outputDir) {
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(outputDir, "outputDir");
        return switch (format) {
            case PDF      -> new PdfPublicationDriver(outputDir);
            case MARKDOWN -> new MarkdownPublicationDriver(outputDir);
            case EPUB     -> new EpubPublicationDriver(outputDir);
        };
    }
}
