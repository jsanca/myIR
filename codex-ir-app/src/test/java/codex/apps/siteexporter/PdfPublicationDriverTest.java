package codex.apps.siteexporter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PdfPublicationDriverTest {

    private static final URI SEED = URI.create("https://book.example.com/");
    private static final Instant FETCHED_AT = Instant.parse("2026-07-04T12:00:00Z");

    // ------------------------------------------------------------------
    // Null guards
    // ------------------------------------------------------------------

    @Test
    void constructorShouldThrowWhenOutputDirIsNull() {
        assertThrows(NullPointerException.class, () -> new PdfPublicationDriver(null));
    }

    @Test
    void publishShouldThrowWhenSourceIsNull(@TempDir final Path tempDir) {
        final PdfPublicationDriver driver = new PdfPublicationDriver(tempDir);
        final PublicationExportOptions options = options(tempDir.resolve("out.pdf"));
        assertThrows(NullPointerException.class, () -> driver.publish(null, options));
    }

    @Test
    void publishShouldThrowWhenOptionsIsNull(@TempDir final Path tempDir) {
        final PdfPublicationDriver driver = new PdfPublicationDriver(tempDir);
        final PublicationSource source = source(tempDir, List.of());
        assertThrows(NullPointerException.class, () -> driver.publish(source, null));
    }

    // ------------------------------------------------------------------
    // requiresAssetProcessing
    // ------------------------------------------------------------------

    @Test
    void requiresAssetProcessingShouldReturnTrue(@TempDir final Path tempDir) {
        assertTrue(new PdfPublicationDriver(tempDir).requiresAssetProcessing());
    }

    // ------------------------------------------------------------------
    // publish — behavior
    // ------------------------------------------------------------------

    @Test
    void publishShouldProduceArtifactWithPdfFormat(@TempDir final Path tempDir)
            throws Exception {
        Files.writeString(tempDir.resolve("page.html"),
                "<!DOCTYPE html><html><body><h1>Hello</h1></body></html>");

        final PublicationArtifact artifact = new PdfPublicationDriver(tempDir)
                .publish(source(tempDir, List.of(
                        page("id-1", "https://book.example.com/page", "page.html", 0L))),
                        options(tempDir.resolve("out.pdf")));

        assertEquals(PublicationFormat.PDF, artifact.format());
    }

    @Test
    void publishShouldWriteValidPdfToOutputPath(@TempDir final Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("page.html"),
                "<!DOCTYPE html><html><body><p>Content</p></body></html>");

        final Path outputPdf = tempDir.resolve("out.pdf");
        new PdfPublicationDriver(tempDir)
                .publish(source(tempDir, List.of(
                        page("id-1", "https://book.example.com/page", "page.html", 0L))),
                        options(outputPdf));

        assertTrue(Files.exists(outputPdf), "output PDF must be created");
        final byte[] bytes = Files.readAllBytes(outputPdf);
        assertTrue(bytes.length >= 4);
        assertEquals('%', (char) bytes[0]);
        assertEquals('P', (char) bytes[1]);
        assertEquals('D', (char) bytes[2]);
        assertEquals('F', (char) bytes[3]);
    }

    @Test
    void publishShouldRenderAllSuccessPages(@TempDir final Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("p1.html"),
                "<!DOCTYPE html><html><body><p>Page one</p></body></html>");
        Files.writeString(tempDir.resolve("p2.html"),
                "<!DOCTYPE html><html><body><p>Page two</p></body></html>");

        final PublicationArtifact artifact = new PdfPublicationDriver(tempDir)
                .publish(source(tempDir, List.of(
                        page("id-1", "https://book.example.com/p1", "p1.html", 0L),
                        page("id-2", "https://book.example.com/p2", "p2.html", 1L))),
                        options(tempDir.resolve("out.pdf")));

        assertEquals(2, artifact.assemblyReport().pagesAttempted());
        assertEquals(2, artifact.assemblyReport().pagesRendered());
        assertEquals(0, artifact.assemblyReport().pagesFailed());
    }

    @Test
    void publishShouldReturnCorrectOutputPath(@TempDir final Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("page.html"),
                "<!DOCTYPE html><html><body><p>OK</p></body></html>");
        final Path outputPdf = tempDir.resolve("out.pdf");

        final PublicationArtifact artifact = new PdfPublicationDriver(tempDir)
                .publish(source(tempDir, List.of(
                        page("id-1", "https://book.example.com/page", "page.html", 0L))),
                        options(outputPdf));

        assertEquals(outputPdf, artifact.path());
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static PublicationSource source(final Path contentDir,
            final List<MirroredPage> pages) {
        return SiteMirrorSource.of(contentDir, MirrorManifest.builder()
                .startUrl(SEED).sameDomainOnly(true).maxPages(50).maxDepth(3)
                .pages(pages).build());
    }

    private static PublicationExportOptions options(final Path outputPath) {
        return PublicationExportOptions.builder()
                .format(PublicationFormat.PDF)
                .outputPath(outputPath)
                .build();
    }

    private static MirroredPage page(final String id, final String url,
            final String localHtmlPath, final long discoveredOrder) {
        return MirroredPage.builder()
                .id(id).url(URI.create(url)).canonicalUrl(URI.create(url))
                .localHtmlPath(localHtmlPath).depth(0).discoveredOrder(discoveredOrder)
                .status(200).fetchedAt(FETCHED_AT).mirrorStatus(MirrorStatus.SUCCESS).build();
    }
}
