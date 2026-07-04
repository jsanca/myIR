package codex.apps.siteexporter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * An assembled, ready-to-run publication pipeline.
 *
 * <p>Construct via {@link #builder()} using the fluent API:</p>
 * <pre>{@code
 * PublicationPipeline pipeline = PublicationPipeline.builder()
 *     .source(SiteMirrorSource.from(mirrorDir))
 *     .renderer(pdfRenderer)
 *     .assemblyStrategy(pdfAssemblyStrategy)
 *     .output(outputPath)
 *     .build();
 *
 * PublicationArtifact artifact = pipeline.run();
 * }</pre>
 *
 * <p>All four builder parameters are required. {@link Builder#build()} throws
 * {@link NullPointerException} if any are absent.</p>
 *
 * <p>{@link #run()} iterates over every {@link MirrorStatus#SUCCESS} page in
 * {@code source.manifest()}, calls {@code renderer.render(htmlFile)} for each,
 * then calls {@code assemblyStrategy.assemble(pages)} to produce the final
 * artifact, which is written to {@code output}.</p>
 */
public final class PublicationPipeline {

    private final PublicationSource source;
    private final PdfRenderer renderer;
    private final PdfAssemblyStrategy assemblyStrategy;
    private final PublicationOrderingStrategy orderingStrategy;
    private final Path output;
    private final PublicationFormat format;

    private PublicationPipeline(final PublicationSource source, final PdfRenderer renderer,
            final PdfAssemblyStrategy assemblyStrategy,
            final PublicationOrderingStrategy orderingStrategy, final Path output,
            final PublicationFormat format) {
        this.source = source;
        this.renderer = renderer;
        this.assemblyStrategy = assemblyStrategy;
        this.orderingStrategy = orderingStrategy;
        this.output = output;
        this.format = format;
    }

    public static Builder builder() {
        return new Builder();
    }

    // ------------------------------------------------------------------
    // Accessors
    // ------------------------------------------------------------------

    public PublicationSource source() { return source; }
    public PdfRenderer renderer() { return renderer; }
    public PdfAssemblyStrategy assemblyStrategy() { return assemblyStrategy; }
    public PublicationOrderingStrategy orderingStrategy() { return orderingStrategy; }
    public Path output() { return output; }
    public PublicationFormat format() { return format; }

    // ------------------------------------------------------------------
    // Execution
    // ------------------------------------------------------------------

    /**
     * Runs the pipeline: renders each mirrored page to PDF, assembles them into
     * one document, writes it to {@link #output()}, and returns the artifact.
     *
     * @return description of the produced artifact
     * @throws IOException if rendering, assembly, or file I/O fails
     */
    public PublicationArtifact run() throws IOException {
        final List<MirroredPage> pages = orderingStrategy.order(source.manifest());

        final List<byte[]> renderedPages = new ArrayList<>(pages.size());
        final List<AssemblyReport.RenderFailure> failures = new ArrayList<>();

        for (final MirroredPage page : pages) {
            final Path htmlFile = source.contentDir().resolve(page.localHtmlPath());
            System.out.printf("[Pipeline] Rendering %s%n", htmlFile);
            try {
                final PdfRenderOptions opts = PdfRenderOptions.forFile(htmlFile);
                renderedPages.add(renderer.render(htmlFile, opts).bytes());
            } catch (final IOException e) {
                final String url = page.url() != null ? page.url().toString() : htmlFile.toString();
                System.err.printf("[Pipeline][WARN] Render failed for %s: %s%n", url, e.getMessage());
                failures.add(new AssemblyReport.RenderFailure(url, e.getMessage()));
            }
        }

        final byte[] assembled = assemblyStrategy.assemble(renderedPages);

        if (output.getParent() != null) {
            Files.createDirectories(output.getParent());
        }
        Files.write(output, assembled);

        final AssemblyReport report = new AssemblyReport(
                pages.size(), renderedPages.size(), failures.size(), assembled.length, failures);

        System.out.printf("[Pipeline] Artifact written → %s (%d bytes, %d/%d pages rendered, %d failed)%n",
                output, assembled.length, renderedPages.size(), pages.size(), failures.size());

        return new PublicationArtifact(output, format, assembled.length, Instant.now(), report);
    }

    // ------------------------------------------------------------------
    // Builder
    // ------------------------------------------------------------------

    public static final class Builder {

        private PublicationSource source;
        private PdfRenderer renderer;
        private PdfAssemblyStrategy assemblyStrategy;
        private PublicationOrderingStrategy orderingStrategy =
                new DiscoveredOrderPublicationOrderingStrategy();
        private Path output;
        private PublicationFormat format = PublicationFormat.PDF;

        private Builder() {}

        public Builder source(final PublicationSource source) {
            this.source = Objects.requireNonNull(source, "source");
            return this;
        }

        public Builder renderer(final PdfRenderer renderer) {
            this.renderer = Objects.requireNonNull(renderer, "renderer");
            return this;
        }

        public Builder assemblyStrategy(final PdfAssemblyStrategy assemblyStrategy) {
            this.assemblyStrategy = Objects.requireNonNull(assemblyStrategy, "assemblyStrategy");
            return this;
        }

        public Builder orderingStrategy(final PublicationOrderingStrategy orderingStrategy) {
            this.orderingStrategy = Objects.requireNonNull(orderingStrategy, "orderingStrategy");
            return this;
        }

        public Builder output(final Path output) {
            this.output = Objects.requireNonNull(output, "output");
            return this;
        }

        public Builder format(final PublicationFormat format) {
            this.format = Objects.requireNonNull(format, "format");
            return this;
        }

        /**
         * Assembles and returns the pipeline.
         *
         * @throws NullPointerException if any of {@code source}, {@code renderer},
         *                              {@code assemblyStrategy}, or {@code output}
         *                              was not provided
         */
        public PublicationPipeline build() {
            Objects.requireNonNull(source, "source is required");
            Objects.requireNonNull(renderer, "renderer is required");
            Objects.requireNonNull(assemblyStrategy, "assemblyStrategy is required");
            Objects.requireNonNull(output, "output is required");
            return new PublicationPipeline(source, renderer, assemblyStrategy, orderingStrategy,
                    output, format);
        }
    }
}
