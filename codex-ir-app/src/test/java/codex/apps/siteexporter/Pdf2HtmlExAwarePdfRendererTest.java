package codex.apps.siteexporter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class Pdf2HtmlExAwarePdfRendererTest {

    private static final Instant FETCHED_AT = Instant.parse("2026-07-04T12:00:00Z");
    private static final URI SEED = URI.create("https://book.example.com/");

    // ------------------------------------------------------------------
    // Constructor null guards
    // ------------------------------------------------------------------

    @Test
    void constructorShouldThrowWhenDelegateIsNull(@TempDir final Path tempDir) {
        assertThrows(NullPointerException.class,
                () -> new Pdf2HtmlExAwarePdfRenderer(null, tempDir.resolve("reader-pages")));
    }

    @Test
    void constructorShouldThrowWhenReaderPagesDirIsNull() {
        assertThrows(NullPointerException.class,
                () -> new Pdf2HtmlExAwarePdfRenderer(fakePdfRenderer(), null));
    }

    // ------------------------------------------------------------------
    // render() null guards
    // ------------------------------------------------------------------

    @Test
    void renderShouldThrowWhenHtmlFileIsNull(@TempDir final Path tempDir) {
        final Pdf2HtmlExAwarePdfRenderer renderer = new Pdf2HtmlExAwarePdfRenderer(
                fakePdfRenderer(), tempDir.resolve("reader-pages"));
        assertThrows(NullPointerException.class,
                () -> renderer.render(null, PdfRenderOptions.defaults()));
    }

    @Test
    void renderShouldThrowWhenOptionsIsNull(@TempDir final Path tempDir) throws Exception {
        final Path htmlFile = tempDir.resolve("page.html");
        Files.writeString(htmlFile, "<html><body><p>Content</p></body></html>");
        final Pdf2HtmlExAwarePdfRenderer renderer = new Pdf2HtmlExAwarePdfRenderer(
                fakePdfRenderer(), tempDir.resolve("reader-pages"));
        assertThrows(NullPointerException.class, () -> renderer.render(htmlFile, null));
    }

    // ------------------------------------------------------------------
    // Normal HTML → direct delegation, no reader path
    // ------------------------------------------------------------------

    @Test
    void renderShouldDelegateDirectlyForNormalHtml(@TempDir final Path tempDir) throws Exception {
        final Path htmlFile = tempDir.resolve("page.html");
        Files.writeString(htmlFile, "<html><body><p>Normal content</p></body></html>");

        final AtomicReference<Path> capturedFile = new AtomicReference<>();
        final PdfRenderer spy = (file, opts) -> {
            capturedFile.set(file);
            return new RenderedPdf(new byte[]{1, 2, 3}, file);
        };

        final Path readerPagesDir = tempDir.resolve("reader-pages");
        new Pdf2HtmlExAwarePdfRenderer(spy, readerPagesDir)
                .render(htmlFile, PdfRenderOptions.forFile(htmlFile));

        assertEquals(htmlFile, capturedFile.get(),
                "normal HTML must be rendered from the original file, not a reader copy");
        assertFalse(Files.exists(readerPagesDir),
                "reader-pages directory must not be created for a normal HTML page");
    }

    @Test
    void renderShouldReturnDelegateResultForNormalHtml(@TempDir final Path tempDir) throws Exception {
        final Path htmlFile = tempDir.resolve("page.html");
        Files.writeString(htmlFile, "<html><body><p>OK</p></body></html>");

        final byte[] expected = new byte[]{10, 20, 30};
        final PdfRenderer stub = (file, opts) -> new RenderedPdf(expected, file);

        final RenderedPdf result = new Pdf2HtmlExAwarePdfRenderer(stub, tempDir.resolve("rp"))
                .render(htmlFile, PdfRenderOptions.forFile(htmlFile));

        assertArrayEquals(expected, result.bytes(),
                "bytes from delegate must be returned unchanged for normal HTML");
    }

    // ------------------------------------------------------------------
    // pdf2htmlEX page → reader path
    // ------------------------------------------------------------------

    @Test
    void renderShouldRouteViaReaderPathForPdf2HtmlExPage(@TempDir final Path tempDir)
            throws Exception {
        final Path htmlFile = tempDir.resolve("chapter.html");
        Files.writeString(htmlFile, Pdf2HtmlExDetectorTest.loadFixture());

        final AtomicReference<Path> capturedFile = new AtomicReference<>();
        final PdfRenderer spy = (file, opts) -> {
            capturedFile.set(file);
            return new RenderedPdf(new byte[]{1, 2, 3}, file);
        };

        final Path readerPagesDir = tempDir.resolve("reader-pages");
        new Pdf2HtmlExAwarePdfRenderer(spy, readerPagesDir)
                .render(htmlFile, PdfRenderOptions.forFile(htmlFile));

        assertNotNull(capturedFile.get());
        assertNotEquals(htmlFile, capturedFile.get(),
                "pdf2htmlEX page must not be rendered from the original file");
        assertTrue(capturedFile.get().getFileName().toString().endsWith(".reader.html"),
                "delegate must be called with a .reader.html file");
    }

    @Test
    void renderShouldWriteReaderHtmlToReaderPagesDir(@TempDir final Path tempDir) throws Exception {
        final Path htmlFile = tempDir.resolve("dl-chapter.html");
        Files.writeString(htmlFile, Pdf2HtmlExDetectorTest.loadFixture());

        final Path readerPagesDir = tempDir.resolve("reader-pages");
        new Pdf2HtmlExAwarePdfRenderer(fakePdfRenderer(), readerPagesDir)
                .render(htmlFile, PdfRenderOptions.forFile(htmlFile));

        final Path readerFile = readerPagesDir.resolve("dl-chapter.html.reader.html");
        assertTrue(Files.exists(readerFile),
                "reader HTML must be written to reader-pages/<filename>.reader.html");
    }

    @Test
    void renderShouldAutoCreateReaderPagesDirWhenAbsent(@TempDir final Path tempDir)
            throws Exception {
        final Path htmlFile = tempDir.resolve("chapter.html");
        Files.writeString(htmlFile, Pdf2HtmlExDetectorTest.loadFixture());

        // Do NOT pre-create reader-pages — the renderer must create it
        final Path readerPagesDir = tempDir.resolve("nested/reader-pages");
        assertFalse(Files.exists(readerPagesDir));

        new Pdf2HtmlExAwarePdfRenderer(fakePdfRenderer(), readerPagesDir)
                .render(htmlFile, PdfRenderOptions.forFile(htmlFile));

        assertTrue(Files.exists(readerPagesDir),
                "reader-pages directory must be created automatically");
    }

    @Test
    void readerHtmlShouldContainExtractedValidationStrings(@TempDir final Path tempDir)
            throws Exception {
        final Path htmlFile = tempDir.resolve("chapter.html");
        Files.writeString(htmlFile, Pdf2HtmlExDetectorTest.loadFixture());

        final Path readerPagesDir = tempDir.resolve("reader-pages");
        new Pdf2HtmlExAwarePdfRenderer(fakePdfRenderer(), readerPagesDir)
                .render(htmlFile, PdfRenderOptions.forFile(htmlFile));

        final String readerHtml = Files.readString(
                readerPagesDir.resolve("chapter.html.reader.html"));

        assertTrue(readerHtml.contains("Part I"),
                "reader HTML must contain 'Part I'");
        assertTrue(readerHtml.contains("Applied Math and Machine Learning Basics"),
                "reader HTML must contain subtitle");
        assertTrue(readerHtml.contains("This part of the book introduces"),
                "reader HTML must contain body text prefix");
    }

    // ------------------------------------------------------------------
    // End-to-end: real OpenHTMLToPDF rendering of a pdf2htmlEX page
    // ------------------------------------------------------------------

    @Test
    void renderShouldProduceValidPdfBytesForPdf2HtmlExPage(@TempDir final Path tempDir)
            throws Exception {
        final Path htmlFile = tempDir.resolve("chapter.html");
        Files.writeString(htmlFile, Pdf2HtmlExDetectorTest.loadFixture());

        final Path readerPagesDir = tempDir.resolve("reader-pages");
        final Pdf2HtmlExAwarePdfRenderer renderer = new Pdf2HtmlExAwarePdfRenderer(
                new OpenHtmlToPdfRenderer(), readerPagesDir);

        final RenderedPdf result = renderer.render(htmlFile, PdfRenderOptions.forFile(htmlFile));

        final byte[] bytes = result.bytes();
        assertTrue(bytes.length >= 4, "rendered PDF must be non-empty");
        assertEquals('%', (char) bytes[0]);
        assertEquals('P', (char) bytes[1]);
        assertEquals('D', (char) bytes[2]);
        assertEquals('F', (char) bytes[3]);
    }

    // ------------------------------------------------------------------
    // Mixed-manifest integration test
    // ------------------------------------------------------------------

    @Test
    void pipelineWithMixedManifestShouldRenderBothPagesAndCreateReaderArtifact(
            @TempDir final Path tempDir) throws Exception {
        // Set up content directory with one normal and one pdf2htmlEX page
        final Path contentDir = tempDir.resolve("content");
        Files.createDirectories(contentDir);

        Files.writeString(contentDir.resolve("normal.html"),
                "<!DOCTYPE html><html><body><h1>Chapter 1</h1><p>Normal content.</p></body></html>");

        Files.writeString(contentDir.resolve("pdf2htmlex.html"),
                Pdf2HtmlExDetectorTest.loadFixture());

        // Manifest with both pages as SUCCESS
        final MirrorManifest manifest = MirrorManifest.builder()
                .startUrl(SEED).sameDomainOnly(true).maxPages(10).maxDepth(2)
                .pages(List.of(
                        successPage("id-1", "https://book.example.com/normal",
                                "normal.html", 0L),
                        successPage("id-2", "https://book.example.com/chapter",
                                "pdf2htmlex.html", 1L)))
                .build();

        final Path readerPagesDir = tempDir.resolve("reader-pages");
        final Path outputPdf = tempDir.resolve("output.pdf");

        final PublicationArtifact artifact = PublicationPipeline.builder()
                .source(SiteMirrorSource.of(contentDir, manifest))
                .renderer(new Pdf2HtmlExAwarePdfRenderer(new OpenHtmlToPdfRenderer(), readerPagesDir))
                .assemblyStrategy(new ManifestOrderPdfAssemblyStrategy())
                .output(outputPdf)
                .build()
                .run();

        // Both pages must have been rendered
        final AssemblyReport report = artifact.assemblyReport();
        assertEquals(2, report.pagesAttempted(), "manifest contains 2 SUCCESS pages");
        assertEquals(2, report.pagesRendered(), "both pages must render successfully");
        assertEquals(0, report.pagesFailed());

        // Reader HTML artifact must exist for the pdf2htmlEX page
        final Path readerHtml = readerPagesDir.resolve("pdf2htmlex.html.reader.html");
        assertTrue(Files.exists(readerHtml),
                "reader HTML must be written for the pdf2htmlEX page");

        // No reader HTML artifact for the normal page
        assertFalse(Files.exists(readerPagesDir.resolve("normal.html.reader.html")),
                "reader HTML must NOT be written for a normal HTML page");

        // Assembled PDF must start with %PDF
        assertTrue(Files.exists(outputPdf), "output PDF must be written");
        final byte[] pdfBytes = Files.readAllBytes(outputPdf);
        assertTrue(pdfBytes.length >= 4);
        assertEquals('%', (char) pdfBytes[0]);
        assertEquals('P', (char) pdfBytes[1]);
        assertEquals('D', (char) pdfBytes[2]);
        assertEquals('F', (char) pdfBytes[3]);
    }

    @Test
    void pipelineOrderingShouldBePreservedAcrossMixedPages(@TempDir final Path tempDir)
            throws Exception {
        final Path contentDir = tempDir.resolve("content");
        Files.createDirectories(contentDir);
        Files.writeString(contentDir.resolve("first.html"),
                "<html><body><p>First</p></body></html>");
        Files.writeString(contentDir.resolve("second.html"),
                Pdf2HtmlExDetectorTest.loadFixture());

        final java.util.List<Path> renderOrder = new java.util.ArrayList<>();
        // Spy delegate records which files are rendered (after routing)
        final PdfRenderer spyDelegate = (file, opts) -> {
            renderOrder.add(file);
            return new RenderedPdf(new byte[]{1, 2, 3}, file);
        };

        final MirrorManifest manifest = MirrorManifest.builder()
                .startUrl(SEED).sameDomainOnly(true).maxPages(10).maxDepth(2)
                .pages(List.of(
                        successPage("id-a", "https://example.com/first", "first.html", 0L),
                        successPage("id-b", "https://example.com/second", "second.html", 1L)))
                .build();

        PublicationPipeline.builder()
                .source(SiteMirrorSource.of(contentDir, manifest))
                .renderer(new Pdf2HtmlExAwarePdfRenderer(spyDelegate, tempDir.resolve("reader-pages")))
                .assemblyStrategy(pages -> new byte[]{99})
                .output(tempDir.resolve("out.pdf"))
                .build()
                .run();

        assertEquals(2, renderOrder.size(), "two pages must be rendered");
        // first.html → rendered as first.html (normal path)
        assertEquals("first.html", renderOrder.get(0).getFileName().toString(),
                "first page must be rendered first (document order)");
        // second.html → rendered as second.html.reader.html (reader path)
        assertEquals("second.html.reader.html", renderOrder.get(1).getFileName().toString(),
                "pdf2htmlEX page must be rendered via reader path, maintaining its position");
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static PdfRenderer fakePdfRenderer() {
        return (file, opts) -> new RenderedPdf(new byte[]{1, 2, 3}, file);
    }

    private static MirroredPage successPage(final String id, final String url,
            final String localHtmlPath, final long discoveredOrder) {
        return MirroredPage.builder()
                .id(id).url(URI.create(url)).canonicalUrl(URI.create(url))
                .localHtmlPath(localHtmlPath).depth(0).discoveredOrder(discoveredOrder)
                .status(200).fetchedAt(FETCHED_AT).mirrorStatus(MirrorStatus.SUCCESS).build();
    }
}
