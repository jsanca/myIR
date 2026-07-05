package codex.apps.siteexporter;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * {@link PublicationDriver} that extracts text from mirrored pages and writes a
 * combined Markdown file.
 *
 * <p>Wiring:</p>
 * <ul>
 *   <li>{@link MarkdownPublicationWriter} handles pdf2htmlEX detection, text
 *       extraction, and normal-HTML paragraph extraction.</li>
 *   <li>Per-page {@code .md} side-output files are written to
 *       {@code outputDir/markdown-pages/}.</li>
 * </ul>
 *
 * <p>Asset download and link rewriting are not needed for Markdown output;
 * {@link #requiresAssetProcessing()} returns {@code false}.</p>
 */
public final class MarkdownPublicationDriver implements PublicationDriver {

    private final Path outputDir;

    /**
     * @param outputDir mirror output directory; {@code markdown-pages/} is created here;
     *                  must not be {@code null}
     */
    public MarkdownPublicationDriver(final Path outputDir) {
        this.outputDir = Objects.requireNonNull(outputDir, "outputDir");
    }

    @Override
    public boolean requiresAssetProcessing() {
        return false;
    }

    @Override
    public PublicationArtifact publish(final PublicationSource source,
            final PublicationExportOptions options) throws IOException {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(options, "options");

        return MarkdownPublicationWriter.builder()
                .source(source)
                .markdownPagesDir(outputDir.resolve("markdown-pages"))
                .build()
                .write(options.outputPath());
    }
}
