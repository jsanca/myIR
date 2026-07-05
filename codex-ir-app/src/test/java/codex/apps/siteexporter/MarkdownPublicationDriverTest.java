package codex.apps.siteexporter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MarkdownPublicationDriverTest {

    private static final URI SEED = URI.create("https://book.example.com/");
    private static final Instant FETCHED_AT = Instant.parse("2026-07-04T12:00:00Z");

    // ------------------------------------------------------------------
    // Null guards
    // ------------------------------------------------------------------

    @Test
    void constructorShouldThrowWhenOutputDirIsNull() {
        assertThrows(NullPointerException.class, () -> new MarkdownPublicationDriver(null));
    }

    @Test
    void publishShouldThrowWhenSourceIsNull(@TempDir final Path tempDir) {
        final MarkdownPublicationDriver driver = new MarkdownPublicationDriver(tempDir);
        final PublicationExportOptions options = options(tempDir.resolve("out.md"));
        assertThrows(NullPointerException.class, () -> driver.publish(null, options));
    }

    @Test
    void publishShouldThrowWhenOptionsIsNull(@TempDir final Path tempDir) {
        final MarkdownPublicationDriver driver = new MarkdownPublicationDriver(tempDir);
        final PublicationSource source = source(tempDir, List.of());
        assertThrows(NullPointerException.class, () -> driver.publish(source, null));
    }

    // ------------------------------------------------------------------
    // requiresAssetProcessing
    // ------------------------------------------------------------------

    @Test
    void requiresAssetProcessingShouldReturnFalse(@TempDir final Path tempDir) {
        assertFalse(new MarkdownPublicationDriver(tempDir).requiresAssetProcessing());
    }

    // ------------------------------------------------------------------
    // publish — behavior
    // ------------------------------------------------------------------

    @Test
    void publishShouldProduceArtifactWithMarkdownFormat(@TempDir final Path tempDir)
            throws Exception {
        Files.writeString(tempDir.resolve("page.html"),
                "<html><body><p>Hello</p></body></html>");

        final PublicationArtifact artifact = new MarkdownPublicationDriver(tempDir)
                .publish(source(tempDir, List.of(
                        page("id-1", "https://book.example.com/page", "page.html", 0L))),
                        options(tempDir.resolve("out.md")));

        assertEquals(PublicationFormat.MARKDOWN, artifact.format());
    }

    @Test
    void publishShouldWriteMarkdownFileToOutputPath(@TempDir final Path tempDir)
            throws Exception {
        Files.writeString(tempDir.resolve("page.html"),
                "<html><body><p>Hello world</p></body></html>");

        final Path outputMd = tempDir.resolve("out.md");
        new MarkdownPublicationDriver(tempDir)
                .publish(source(tempDir, List.of(
                        page("id-1", "https://book.example.com/page", "page.html", 0L))),
                        options(outputMd));

        assertTrue(Files.exists(outputMd), "output .md file must be created");
        assertTrue(Files.readString(outputMd).contains("Hello world"),
                "extracted paragraph must appear in Markdown output");
    }

    @Test
    void publishShouldExtractTextFromNormalHtml(@TempDir final Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("page.html"),
                "<html><body><h1>Title</h1><p>Body text.</p></body></html>");

        final Path outputMd = tempDir.resolve("out.md");
        new MarkdownPublicationDriver(tempDir)
                .publish(source(tempDir, List.of(
                        page("id-1", "https://book.example.com/page", "page.html", 0L))),
                        options(outputMd));

        final String md = Files.readString(outputMd);
        assertTrue(md.contains("Title"), "heading text must appear in Markdown output");
        assertTrue(md.contains("Body text."), "paragraph text must appear in Markdown output");
    }

    @Test
    void publishShouldCreateMarkdownPagesDir(@TempDir final Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("page.html"),
                "<html><body><p>Content</p></body></html>");

        new MarkdownPublicationDriver(tempDir)
                .publish(source(tempDir, List.of(
                        page("id-1", "https://book.example.com/page", "page.html", 0L))),
                        options(tempDir.resolve("out.md")));

        assertTrue(Files.exists(tempDir.resolve("markdown-pages")),
                "markdown-pages directory must be created under outputDir");
        assertTrue(Files.exists(tempDir.resolve("markdown-pages/page.html.md")),
                "per-page .md file must be written");
    }

    @Test
    void publishShouldReturnCorrectOutputPath(@TempDir final Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("page.html"),
                "<html><body><p>OK</p></body></html>");
        final Path outputMd = tempDir.resolve("out.md");

        final PublicationArtifact artifact = new MarkdownPublicationDriver(tempDir)
                .publish(source(tempDir, List.of(
                        page("id-1", "https://book.example.com/page", "page.html", 0L))),
                        options(outputMd));

        assertEquals(outputMd, artifact.path());
    }

    @Test
    void publishShouldReturnCorrectPageCountInAssemblyReport(@TempDir final Path tempDir)
            throws Exception {
        Files.writeString(tempDir.resolve("p1.html"),
                "<html><body><p>One</p></body></html>");
        Files.writeString(tempDir.resolve("p2.html"),
                "<html><body><p>Two</p></body></html>");

        final PublicationArtifact artifact = new MarkdownPublicationDriver(tempDir)
                .publish(source(tempDir, List.of(
                        page("id-1", "https://book.example.com/p1", "p1.html", 0L),
                        page("id-2", "https://book.example.com/p2", "p2.html", 1L))),
                        options(tempDir.resolve("out.md")));

        assertEquals(2, artifact.assemblyReport().pagesAttempted());
        assertEquals(2, artifact.assemblyReport().pagesRendered());
        assertEquals(0, artifact.assemblyReport().pagesFailed());
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
                .format(PublicationFormat.MARKDOWN)
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
