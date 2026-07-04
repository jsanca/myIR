package codex.apps.siteexporter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class PublicationPipelineTest {

    private static final URI SEED = URI.create("https://example.com/");
    private static final Instant FETCHED_AT = Instant.parse("2026-07-03T12:00:00Z");

    // ------------------------------------------------------------------
    // Builder validation
    // ------------------------------------------------------------------

    @Test
    void buildShouldSucceedWithAllRequiredComponents(@TempDir final Path tempDir) {
        final PublicationPipeline pipeline = PublicationPipeline.builder()
                .source(sourceWith(tempDir, List.of()))
                .renderer(fakePdfRenderer())
                .assemblyStrategy(concatAssembler())
                .output(tempDir.resolve("output.pdf"))
                .build();

        assertNotNull(pipeline);
    }

    @Test
    void buildShouldThrowWhenSourceIsMissing(@TempDir final Path tempDir) {
        assertThrows(NullPointerException.class, () ->
                PublicationPipeline.builder()
                        .renderer(fakePdfRenderer())
                        .assemblyStrategy(concatAssembler())
                        .output(tempDir.resolve("output.pdf"))
                        .build());
    }

    @Test
    void buildShouldThrowWhenRendererIsMissing(@TempDir final Path tempDir) {
        assertThrows(NullPointerException.class, () ->
                PublicationPipeline.builder()
                        .source(sourceWith(tempDir, List.of()))
                        .assemblyStrategy(concatAssembler())
                        .output(tempDir.resolve("output.pdf"))
                        .build());
    }

    @Test
    void buildShouldThrowWhenAssemblyStrategyIsMissing(@TempDir final Path tempDir) {
        assertThrows(NullPointerException.class, () ->
                PublicationPipeline.builder()
                        .source(sourceWith(tempDir, List.of()))
                        .renderer(fakePdfRenderer())
                        .output(tempDir.resolve("output.pdf"))
                        .build());
    }

    @Test
    void buildShouldThrowWhenOutputIsMissing(@TempDir final Path tempDir) {
        assertThrows(NullPointerException.class, () ->
                PublicationPipeline.builder()
                        .source(sourceWith(tempDir, List.of()))
                        .renderer(fakePdfRenderer())
                        .assemblyStrategy(concatAssembler())
                        .build());
    }

    // ------------------------------------------------------------------
    // Accessor tests
    // ------------------------------------------------------------------

    @Test
    void accessorsShouldReturnProvidedComponents(@TempDir final Path tempDir) {
        final PublicationSource source = sourceWith(tempDir, List.of());
        final PdfRenderer renderer = fakePdfRenderer();
        final PdfAssemblyStrategy strategy = concatAssembler();
        final Path output = tempDir.resolve("output.pdf");

        final PublicationPipeline pipeline = PublicationPipeline.builder()
                .source(source).renderer(renderer).assemblyStrategy(strategy).output(output).build();

        assertSame(source, pipeline.source());
        assertSame(renderer, pipeline.renderer());
        assertSame(strategy, pipeline.assemblyStrategy());
        assertEquals(output, pipeline.output());
        assertEquals(PublicationFormat.PDF, pipeline.format(), "default format must be PDF");
    }

    @Test
    void formatDefaultsToPdf(@TempDir final Path tempDir) {
        final PublicationPipeline pipeline = PublicationPipeline.builder()
                .source(sourceWith(tempDir, List.of()))
                .renderer(fakePdfRenderer())
                .assemblyStrategy(concatAssembler())
                .output(tempDir.resolve("output.pdf"))
                .build();

        assertEquals(PublicationFormat.PDF, pipeline.format());
    }

    @Test
    void formatCanBeOverriddenToEpub(@TempDir final Path tempDir) {
        final PublicationPipeline pipeline = PublicationPipeline.builder()
                .source(sourceWith(tempDir, List.of()))
                .renderer(fakePdfRenderer())
                .assemblyStrategy(concatAssembler())
                .output(tempDir.resolve("output.epub"))
                .format(PublicationFormat.EPUB)
                .build();

        assertEquals(PublicationFormat.EPUB, pipeline.format());
    }

    // ------------------------------------------------------------------
    // SiteMirrorSource
    // ------------------------------------------------------------------

    @Test
    void siteMirrorSourceOfShouldExposeContentDirAndManifest(@TempDir final Path tempDir) {
        final MirrorManifest manifest = emptyManifest();
        final SiteMirrorSource source = SiteMirrorSource.of(tempDir, manifest);

        assertEquals(tempDir, source.contentDir());
        assertSame(manifest, source.manifest());
    }

    @Test
    void siteMirrorSourceFromShouldReadManifestFromDisk(@TempDir final Path tempDir) throws Exception {
        final MirrorManifest written = emptyManifest();
        written.writeTo(tempDir);

        final SiteMirrorSource source = SiteMirrorSource.from(tempDir);

        assertEquals(tempDir, source.contentDir());
        assertEquals(written.startUrl(), source.manifest().startUrl());
        assertEquals(0, source.manifest().documentCount());
    }

    // ------------------------------------------------------------------
    // Ordering strategy
    // ------------------------------------------------------------------

    @Test
    void defaultOrderingStrategyShouldBeDiscoveredOrder(@TempDir final Path tempDir) {
        final PublicationPipeline pipeline = PublicationPipeline.builder()
                .source(sourceWith(tempDir, List.of()))
                .renderer(fakePdfRenderer())
                .assemblyStrategy(concatAssembler())
                .output(tempDir.resolve("output.pdf"))
                .build();

        assertNotNull(pipeline.orderingStrategy());
        assertInstanceOf(DiscoveredOrderPublicationOrderingStrategy.class,
                pipeline.orderingStrategy());
    }

    @Test
    void orderingStrategyCanBeOverridden(@TempDir final Path tempDir) {
        final PublicationOrderingStrategy custom = manifest -> List.of();
        final PublicationPipeline pipeline = PublicationPipeline.builder()
                .source(sourceWith(tempDir, List.of()))
                .renderer(fakePdfRenderer())
                .assemblyStrategy(concatAssembler())
                .orderingStrategy(custom)
                .output(tempDir.resolve("output.pdf"))
                .build();

        assertSame(custom, pipeline.orderingStrategy());
    }

    @Test
    void orderingStrategyShouldNullThrowInBuilder(@TempDir final Path tempDir) {
        assertThrows(NullPointerException.class, () ->
                PublicationPipeline.builder()
                        .source(sourceWith(tempDir, List.of()))
                        .renderer(fakePdfRenderer())
                        .assemblyStrategy(concatAssembler())
                        .orderingStrategy(null)
                        .output(tempDir.resolve("output.pdf"))
                        .build());
    }

    @Test
    void runShouldRespectOrderingStrategyOrder(@TempDir final Path tempDir) throws Exception {
        writeHtml(tempDir, "first.html", "<html>First</html>");
        writeHtml(tempDir, "second.html", "<html>Second</html>");

        final java.util.List<Path> renderedOrder = new java.util.ArrayList<>();
        final PdfRenderer recordingRenderer = (htmlFile, opts) -> {
            renderedOrder.add(htmlFile);
            return new RenderedPdf(new byte[]{1}, htmlFile);
        };

        final MirroredPage pageA = successPage("id-a", "https://example.com/a", "first.html");
        final MirroredPage pageB = successPage("id-b", "https://example.com/b", "second.html");
        final MirrorManifest manifest = manifestWithPages(pageA, pageB);

        // Custom strategy reverses order
        final PublicationOrderingStrategy reverseStrategy = m -> List.of(pageB, pageA);

        PublicationPipeline.builder()
                .source(SiteMirrorSource.of(tempDir, manifest))
                .renderer(recordingRenderer)
                .assemblyStrategy(concatAssembler())
                .orderingStrategy(reverseStrategy)
                .output(tempDir.resolve("out.pdf"))
                .build()
                .run();

        assertEquals(2, renderedOrder.size());
        assertTrue(renderedOrder.get(0).toString().endsWith("second.html"),
                "first rendered file should be second.html per custom strategy");
        assertTrue(renderedOrder.get(1).toString().endsWith("first.html"),
                "second rendered file should be first.html per custom strategy");
    }

    @Test
    void runShouldPassOrderedPagesToAssemblyStrategy(@TempDir final Path tempDir) throws Exception {
        writeHtml(tempDir, "p1.html", "<html>P1</html>");
        writeHtml(tempDir, "p2.html", "<html>P2</html>");

        final java.util.List<byte[]> capturedPages = new java.util.ArrayList<>();
        final PdfAssemblyStrategy capturingAssembler = pages -> {
            capturedPages.addAll(pages);
            return new byte[]{99};
        };

        final MirroredPage pageA = successPage("id-a", "https://example.com/a", "p1.html");
        final MirroredPage pageB = successPage("id-b", "https://example.com/b", "p2.html");
        final MirrorManifest manifest = manifestWithPages(pageA, pageB);

        // Strategy returns only pageB
        final PublicationOrderingStrategy singlePageStrategy = m -> List.of(pageB);

        PublicationPipeline.builder()
                .source(SiteMirrorSource.of(tempDir, manifest))
                .renderer(fakePdfRenderer())
                .assemblyStrategy(capturingAssembler)
                .orderingStrategy(singlePageStrategy)
                .output(tempDir.resolve("out.pdf"))
                .build()
                .run();

        assertEquals(1, capturedPages.size(), "assembler should receive only pages the strategy selected");
    }

    // ------------------------------------------------------------------
    // run() with fake components
    // ------------------------------------------------------------------

    @Test
    void runShouldCallRendererOncePerSuccessfulPage(@TempDir final Path tempDir) throws Exception {
        writeHtml(tempDir, "index.html", "<html>Home</html>");
        writeHtml(tempDir, "about/index.html", "<html>About</html>");

        final AtomicInteger renderCount = new AtomicInteger();
        final PdfRenderer countingRenderer = (htmlFile, opts) -> {
            renderCount.incrementAndGet();
            return new RenderedPdf(new byte[]{1, 2, 3}, htmlFile);
        };

        final MirrorManifest manifest = manifestWithPages(
                successPage("id-1", "https://example.com/", "index.html"),
                successPage("id-2", "https://example.com/about", "about/index.html"));

        PublicationPipeline.builder()
                .source(SiteMirrorSource.of(tempDir, manifest))
                .renderer(countingRenderer)
                .assemblyStrategy(concatAssembler())
                .output(tempDir.resolve("output.pdf"))
                .build()
                .run();

        assertEquals(2, renderCount.get(), "renderer must be called once per SUCCESS page");
    }

    @Test
    void runShouldSkipWriteFailedPages(@TempDir final Path tempDir) throws Exception {
        writeHtml(tempDir, "index.html", "<html>Home</html>");

        final AtomicInteger renderCount = new AtomicInteger();
        final MirroredPage failed = MirroredPage.builder()
                .id("id-fail").url(URI.create("https://example.com/bad"))
                .canonicalUrl(URI.create("https://example.com/bad"))
                .depth(0).discoveredOrder(1L).status(200).fetchedAt(FETCHED_AT)
                .mirrorStatus(MirrorStatus.WRITE_FAILED).errorMessage("disk full").build();

        final MirrorManifest manifest = manifestWithPages(
                successPage("id-1", "https://example.com/", "index.html"), failed);

        PublicationPipeline.builder()
                .source(SiteMirrorSource.of(tempDir, manifest))
                .renderer((htmlFile, opts) -> { renderCount.incrementAndGet(); return new RenderedPdf(new byte[]{1}, htmlFile); })
                .assemblyStrategy(concatAssembler())
                .output(tempDir.resolve("out.pdf"))
                .build()
                .run();

        assertEquals(1, renderCount.get(), "WRITE_FAILED pages must be excluded from rendering");
    }

    @Test
    void runShouldCallAssemblyStrategyWithAllRenderedPages(@TempDir final Path tempDir)
            throws Exception {
        writeHtml(tempDir, "p1.html", "<html>P1</html>");
        writeHtml(tempDir, "p2.html", "<html>P2</html>");

        final List<byte[]> capturedPages = new java.util.ArrayList<>();
        final PdfAssemblyStrategy capturingAssembler = pages -> {
            capturedPages.addAll(pages);
            return new byte[]{99};
        };

        final MirrorManifest manifest = manifestWithPages(
                successPage("id-1", "https://example.com/p1", "p1.html"),
                successPage("id-2", "https://example.com/p2", "p2.html"));

        PublicationPipeline.builder()
                .source(SiteMirrorSource.of(tempDir, manifest))
                .renderer((htmlFile, opts) -> new RenderedPdf(new byte[]{1}, htmlFile))
                .assemblyStrategy(capturingAssembler)
                .output(tempDir.resolve("out.pdf"))
                .build()
                .run();

        assertEquals(2, capturedPages.size(), "assembler must receive all rendered pages");
    }

    @Test
    void runShouldWriteAssembledBytesToOutputFile(@TempDir final Path tempDir) throws Exception {
        writeHtml(tempDir, "index.html", "<html>Home</html>");

        final byte[] expectedBytes = new byte[]{10, 20, 30, 40};
        final MirrorManifest manifest = manifestWithPages(
                successPage("id-1", "https://example.com/", "index.html"));
        final Path outputFile = tempDir.resolve("out/result.pdf");

        PublicationPipeline.builder()
                .source(SiteMirrorSource.of(tempDir, manifest))
                .renderer((htmlFile, opts) -> new RenderedPdf(new byte[]{1}, htmlFile))
                .assemblyStrategy(pages -> expectedBytes)
                .output(outputFile)
                .build()
                .run();

        assertTrue(Files.exists(outputFile), "output file must be created");
        assertArrayEquals(expectedBytes, Files.readAllBytes(outputFile),
                "output file content must match assembled bytes");
    }

    @Test
    void runShouldReturnArtifactWithCorrectMetadata(@TempDir final Path tempDir) throws Exception {
        writeHtml(tempDir, "index.html", "<html>Home</html>");

        final byte[] assembled = new byte[]{1, 2, 3, 4, 5};
        final MirrorManifest manifest = manifestWithPages(
                successPage("id-1", "https://example.com/", "index.html"));
        final Path outputFile = tempDir.resolve("output.pdf");

        final PublicationArtifact artifact = PublicationPipeline.builder()
                .source(SiteMirrorSource.of(tempDir, manifest))
                .renderer((htmlFile, opts) -> new RenderedPdf(new byte[]{1}, htmlFile))
                .assemblyStrategy(pages -> assembled)
                .output(outputFile)
                .format(PublicationFormat.PDF)
                .build()
                .run();

        assertEquals(outputFile, artifact.path());
        assertEquals(PublicationFormat.PDF, artifact.format());
        assertEquals(assembled.length, artifact.sizeBytes());
        assertNotNull(artifact.producedAt());
        assertNotNull(artifact.assemblyReport());
    }

    @Test
    void runWithEmptyManifestShouldProduceEmptyArtifact(@TempDir final Path tempDir)
            throws Exception {
        final MirrorManifest manifest = emptyManifest();

        final PublicationArtifact artifact = PublicationPipeline.builder()
                .source(SiteMirrorSource.of(tempDir, manifest))
                .renderer((htmlFile, opts) -> new RenderedPdf(new byte[]{1}, htmlFile))
                .assemblyStrategy(pages -> new byte[0])
                .output(tempDir.resolve("out.pdf"))
                .build()
                .run();

        assertEquals(0, artifact.sizeBytes());
        assertEquals(0, artifact.assemblyReport().pagesAttempted());
    }

    // ------------------------------------------------------------------
    // AssemblyReport
    // ------------------------------------------------------------------

    @Test
    void assemblyReportShouldReflectSuccessfulRun(@TempDir final Path tempDir) throws Exception {
        writeHtml(tempDir, "p1.html", "<html>P1</html>");
        writeHtml(tempDir, "p2.html", "<html>P2</html>");

        final MirrorManifest manifest = manifestWithPages(
                successPage("id-1", "https://example.com/p1", "p1.html"),
                successPage("id-2", "https://example.com/p2", "p2.html"));

        final PublicationArtifact artifact = PublicationPipeline.builder()
                .source(SiteMirrorSource.of(tempDir, manifest))
                .renderer((htmlFile, opts) -> new RenderedPdf(new byte[]{1}, htmlFile))
                .assemblyStrategy(concatAssembler())
                .output(tempDir.resolve("out.pdf"))
                .build()
                .run();

        final AssemblyReport report = artifact.assemblyReport();
        assertEquals(2, report.pagesAttempted());
        assertEquals(2, report.pagesRendered());
        assertEquals(0, report.pagesFailed());
        assertTrue(report.renderFailures().isEmpty());
        assertEquals(artifact.sizeBytes(), report.outputSizeBytes());
    }

    @Test
    void assemblyReportShouldRecordRenderFailureAndContinue(@TempDir final Path tempDir)
            throws Exception {
        writeHtml(tempDir, "good.html", "<html>Good</html>");

        final MirrorManifest manifest = manifestWithPages(
                successPage("id-ok", "https://example.com/good", "good.html"),
                successPage("id-bad", "https://example.com/bad", "missing.html"));

        final AtomicInteger renderCount = new AtomicInteger();
        final PdfRenderer flakyRenderer = (htmlFile, opts) -> {
            renderCount.incrementAndGet();
            if (htmlFile.getFileName().toString().equals("missing.html")) {
                throw new java.io.IOException("file not found");
            }
            return new RenderedPdf(new byte[]{1, 2, 3}, htmlFile);
        };

        final PublicationArtifact artifact = PublicationPipeline.builder()
                .source(SiteMirrorSource.of(tempDir, manifest))
                .renderer(flakyRenderer)
                .assemblyStrategy(concatAssembler())
                .output(tempDir.resolve("out.pdf"))
                .build()
                .run();

        final AssemblyReport report = artifact.assemblyReport();
        assertEquals(2, report.pagesAttempted(), "both pages were attempted");
        assertEquals(1, report.pagesRendered(), "only one page rendered successfully");
        assertEquals(1, report.pagesFailed(), "one page failed");
        assertEquals(1, report.renderFailures().size());
        assertEquals("https://example.com/bad", report.renderFailures().get(0).pageUrl());
        assertEquals(2, renderCount.get(), "renderer must be called for every page");
    }

    @Test
    void assemblyReportShouldContainOutputSizeBytes(@TempDir final Path tempDir) throws Exception {
        writeHtml(tempDir, "index.html", "<html>Home</html>");

        final byte[] assembled = new byte[]{10, 20, 30};
        final MirrorManifest manifest = manifestWithPages(
                successPage("id-1", "https://example.com/", "index.html"));

        final PublicationArtifact artifact = PublicationPipeline.builder()
                .source(SiteMirrorSource.of(tempDir, manifest))
                .renderer((htmlFile, opts) -> new RenderedPdf(new byte[]{1}, htmlFile))
                .assemblyStrategy(pages -> assembled)
                .output(tempDir.resolve("out.pdf"))
                .build()
                .run();

        assertEquals(assembled.length, artifact.assemblyReport().outputSizeBytes());
    }

    @Test
    void allRenderFailuresShouldNotAbortPipeline(@TempDir final Path tempDir) throws Exception {
        final MirrorManifest manifest = manifestWithPages(
                successPage("id-1", "https://example.com/a", "missing-a.html"),
                successPage("id-2", "https://example.com/b", "missing-b.html"));

        final PdfRenderer alwaysFails = (htmlFile, opts) -> {
            throw new java.io.IOException("always fails");
        };

        final PublicationArtifact artifact = PublicationPipeline.builder()
                .source(SiteMirrorSource.of(tempDir, manifest))
                .renderer(alwaysFails)
                .assemblyStrategy(pages -> new byte[0])
                .output(tempDir.resolve("out.pdf"))
                .build()
                .run();

        final AssemblyReport report = artifact.assemblyReport();
        assertEquals(2, report.pagesAttempted());
        assertEquals(0, report.pagesRendered());
        assertEquals(2, report.pagesFailed());
        assertEquals(0, artifact.sizeBytes(), "empty assembly should produce zero-byte artifact");
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static PdfRenderer fakePdfRenderer() {
        return (htmlFile, opts) -> new RenderedPdf(new byte[]{1, 2, 3}, htmlFile);
    }

    private static PdfAssemblyStrategy concatAssembler() {
        return pages -> {
            int total = pages.stream().mapToInt(b -> b.length).sum();
            final byte[] result = new byte[total];
            int pos = 0;
            for (final byte[] page : pages) {
                System.arraycopy(page, 0, result, pos, page.length);
                pos += page.length;
            }
            return result;
        };
    }

    private static PublicationSource sourceWith(final Path contentDir,
            final List<MirroredPage> pages) {
        return SiteMirrorSource.of(contentDir, manifestWithPages(pages.toArray(new MirroredPage[0])));
    }

    private static void writeHtml(final Path outputDir, final String relativePath, final String html)
            throws Exception {
        final Path file = outputDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, html);
    }

    private static MirrorManifest emptyManifest() {
        return MirrorManifest.builder()
                .startUrl(SEED).sameDomainOnly(true).maxPages(10).maxDepth(2)
                .pages(List.of()).build();
    }

    private static MirrorManifest manifestWithPages(final MirroredPage... pages) {
        return MirrorManifest.builder()
                .startUrl(SEED).sameDomainOnly(true).maxPages(10).maxDepth(2)
                .pages(List.of(pages)).build();
    }

    private static MirroredPage successPage(final String id, final String url,
            final String localHtmlPath) {
        return MirroredPage.builder()
                .id(id).url(URI.create(url)).canonicalUrl(URI.create(url))
                .localHtmlPath(localHtmlPath).depth(0).discoveredOrder(0L)
                .status(200).fetchedAt(FETCHED_AT).mirrorStatus(MirrorStatus.SUCCESS).build();
    }
}
