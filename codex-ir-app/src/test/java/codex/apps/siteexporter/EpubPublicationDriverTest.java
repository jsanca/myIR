package codex.apps.siteexporter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;

class EpubPublicationDriverTest {

    private static final URI SEED = URI.create("https://book.example.com/");
    private static final Instant FETCHED_AT = Instant.parse("2026-07-04T12:00:00Z");

    // ------------------------------------------------------------------
    // Null guards
    // ------------------------------------------------------------------

    @Test
    void constructorShouldThrowWhenOutputDirIsNull() {
        assertThrows(NullPointerException.class, () -> new EpubPublicationDriver(null));
    }

    @Test
    void publishShouldThrowWhenSourceIsNull(@TempDir final Path tempDir) {
        final EpubPublicationDriver driver = new EpubPublicationDriver(tempDir);
        final PublicationExportOptions options = options(tempDir.resolve("out.epub"));
        assertThrows(NullPointerException.class, () -> driver.publish(null, options));
    }

    @Test
    void publishShouldThrowWhenOptionsIsNull(@TempDir final Path tempDir) {
        final EpubPublicationDriver driver = new EpubPublicationDriver(tempDir);
        final PublicationSource source = source(tempDir, List.of());
        assertThrows(NullPointerException.class, () -> driver.publish(source, null));
    }

    // ------------------------------------------------------------------
    // requiresAssetProcessing
    // ------------------------------------------------------------------

    @Test
    void requiresAssetProcessingShouldReturnFalse(@TempDir final Path tempDir) {
        assertFalse(new EpubPublicationDriver(tempDir).requiresAssetProcessing());
    }

    // ------------------------------------------------------------------
    // Artifact metadata
    // ------------------------------------------------------------------

    @Test
    void publishShouldReturnEpubFormat(@TempDir final Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("page.html"),
                "<html><body><p>Content</p></body></html>");

        final PublicationArtifact artifact = new EpubPublicationDriver(tempDir)
                .publish(source(tempDir, List.of(
                        page("id-1", "https://book.example.com/page", "page.html", 0L))),
                        options(tempDir.resolve("out.epub")));

        assertEquals(PublicationFormat.EPUB, artifact.format());
    }

    @Test
    void publishShouldReturnCorrectOutputPath(@TempDir final Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("page.html"),
                "<html><body><p>Content</p></body></html>");
        final Path outputEpub = tempDir.resolve("out.epub");

        final PublicationArtifact artifact = new EpubPublicationDriver(tempDir)
                .publish(source(tempDir, List.of(
                        page("id-1", "https://book.example.com/page", "page.html", 0L))),
                        options(outputEpub));

        assertEquals(outputEpub, artifact.path());
    }

    @Test
    void publishShouldReturnPositiveSizeBytes(@TempDir final Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("page.html"),
                "<html><body><p>Content</p></body></html>");

        final PublicationArtifact artifact = new EpubPublicationDriver(tempDir)
                .publish(source(tempDir, List.of(
                        page("id-1", "https://book.example.com/page", "page.html", 0L))),
                        options(tempDir.resolve("out.epub")));

        assertTrue(artifact.sizeBytes() > 0);
    }

    // ------------------------------------------------------------------
    // Output file exists and is a ZIP
    // ------------------------------------------------------------------

    @Test
    void publishShouldCreateEpubFile(@TempDir final Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("page.html"),
                "<html><body><p>Content</p></body></html>");
        final Path outputEpub = tempDir.resolve("out.epub");

        new EpubPublicationDriver(tempDir)
                .publish(source(tempDir, List.of(
                        page("id-1", "https://book.example.com/page", "page.html", 0L))),
                        options(outputEpub));

        assertTrue(Files.exists(outputEpub), "EPUB file must be created");
        assertTrue(Files.size(outputEpub) > 0, "EPUB file must be non-empty");
    }

    @Test
    void publishShouldCreateOutputParentDirectoriesIfAbsent(@TempDir final Path tempDir)
            throws Exception {
        Files.writeString(tempDir.resolve("page.html"),
                "<html><body><p>Content</p></body></html>");
        final Path outputEpub = tempDir.resolve("nested/out.epub");

        new EpubPublicationDriver(tempDir)
                .publish(source(tempDir, List.of(
                        page("id-1", "https://book.example.com/page", "page.html", 0L))),
                        options(outputEpub));

        assertTrue(Files.exists(outputEpub));
    }

    // ------------------------------------------------------------------
    // EPUB ZIP structure validation
    // ------------------------------------------------------------------

    @Test
    void epubShouldContainMimetypeEntry(@TempDir final Path tempDir) throws Exception {
        publishOnePageEpub(tempDir);
        assertTrue(epubEntries(tempDir.resolve("out.epub")).contains("mimetype"),
                "EPUB must contain a mimetype entry");
    }

    @Test
    void epubMimetypeShouldBeApplicationEpubZip(@TempDir final Path tempDir) throws Exception {
        publishOnePageEpub(tempDir);
        final String content = readEpubEntry(tempDir.resolve("out.epub"), "mimetype");
        assertEquals("application/epub+zip", content,
                "mimetype entry must contain exactly 'application/epub+zip'");
    }

    @Test
    void epubShouldContainContainerXml(@TempDir final Path tempDir) throws Exception {
        publishOnePageEpub(tempDir);
        assertTrue(epubEntries(tempDir.resolve("out.epub")).contains("META-INF/container.xml"),
                "EPUB must contain META-INF/container.xml");
    }

    @Test
    void epubShouldContainContentOpf(@TempDir final Path tempDir) throws Exception {
        publishOnePageEpub(tempDir);
        assertTrue(epubEntries(tempDir.resolve("out.epub")).contains("OEBPS/content.opf"),
                "EPUB must contain OEBPS/content.opf");
    }

    @Test
    void epubShouldContainNavXhtml(@TempDir final Path tempDir) throws Exception {
        publishOnePageEpub(tempDir);
        assertTrue(epubEntries(tempDir.resolve("out.epub")).contains("OEBPS/nav.xhtml"),
                "EPUB must contain OEBPS/nav.xhtml");
    }

    @Test
    void epubShouldContainChapterFile(@TempDir final Path tempDir) throws Exception {
        publishOnePageEpub(tempDir);
        assertTrue(epubEntries(tempDir.resolve("out.epub")).contains("OEBPS/chapter-001.xhtml"),
                "EPUB must contain chapter-001.xhtml for the first page");
    }

    @Test
    void contentOpfShouldListChaptersInManifestAndSpine(@TempDir final Path tempDir)
            throws Exception {
        Files.writeString(tempDir.resolve("p1.html"),
                "<html><body><p>One</p></body></html>");
        Files.writeString(tempDir.resolve("p2.html"),
                "<html><body><p>Two</p></body></html>");
        new EpubPublicationDriver(tempDir)
                .publish(source(tempDir, List.of(
                        page("id-1", "https://book.example.com/p1", "p1.html", 0L),
                        page("id-2", "https://book.example.com/p2", "p2.html", 1L))),
                        options(tempDir.resolve("out.epub")));

        final String opf = readEpubEntry(tempDir.resolve("out.epub"), "OEBPS/content.opf");
        assertNotNull(opf);
        assertTrue(opf.contains("chapter-001"), "OPF manifest must reference chapter-001");
        assertTrue(opf.contains("chapter-002"), "OPF manifest must reference chapter-002");
        assertTrue(opf.contains("<itemref idref=\"chapter-001\""),
                "OPF spine must include chapter-001");
        assertTrue(opf.contains("<itemref idref=\"chapter-002\""),
                "OPF spine must include chapter-002");
    }

    // ------------------------------------------------------------------
    // Content extraction
    // ------------------------------------------------------------------

    @Test
    void epubChapterShouldContainExtractedParagraphText(@TempDir final Path tempDir)
            throws Exception {
        Files.writeString(tempDir.resolve("page.html"),
                "<html><body><h1>My Title</h1><p>Hello world</p></body></html>");
        new EpubPublicationDriver(tempDir)
                .publish(source(tempDir, List.of(
                        page("id-1", "https://book.example.com/page", "page.html", 0L))),
                        options(tempDir.resolve("out.epub")));

        final String chapter = readEpubEntry(tempDir.resolve("out.epub"),
                "OEBPS/chapter-001.xhtml");
        assertNotNull(chapter);
        assertTrue(chapter.contains("Hello world"),
                "chapter XHTML must contain extracted paragraph text");
    }

    @Test
    void epubChapterShouldContainPdf2HtmlExContent(@TempDir final Path tempDir)
            throws Exception {
        Files.writeString(tempDir.resolve("chapter.html"),
                Pdf2HtmlExDetectorTest.loadFixture());
        new EpubPublicationDriver(tempDir)
                .publish(source(tempDir, List.of(
                        page("id-1", "https://book.example.com/chapter",
                                "chapter.html", 0L))),
                        options(tempDir.resolve("out.epub")));

        final String chapter = readEpubEntry(tempDir.resolve("out.epub"),
                "OEBPS/chapter-001.xhtml");
        assertNotNull(chapter);
        assertTrue(chapter.contains("Part I"),
                "chapter must contain 'Part I' from pdf2htmlEX fixture");
        assertTrue(chapter.contains("Applied Math and Machine Learning Basics"),
                "chapter must contain subtitle");
        assertTrue(chapter.contains("This part of the book introduces"),
                "chapter must contain body text");
    }

    @Test
    void epubChapterShouldNotContainCssOrScriptsFromPdf2HtmlExPage(
            @TempDir final Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("chapter.html"),
                Pdf2HtmlExDetectorTest.loadFixture());
        new EpubPublicationDriver(tempDir)
                .publish(source(tempDir, List.of(
                        page("id-1", "https://book.example.com/chapter", "chapter.html", 0L))),
                        options(tempDir.resolve("out.epub")));

        final String chapter = readEpubEntry(tempDir.resolve("out.epub"),
                "OEBPS/chapter-001.xhtml");
        assertNotNull(chapter);
        assertFalse(chapter.contains("@font-face"),
                "chapter must not contain @font-face from original pdf2htmlEX page");
        assertFalse(chapter.contains("position:absolute"),
                "chapter must not contain absolute positioning CSS");
    }

    // ------------------------------------------------------------------
    // Chapter ordering
    // ------------------------------------------------------------------

    @Test
    void epubChapterOrderShouldFollowDiscoveredOrder(@TempDir final Path tempDir)
            throws Exception {
        Files.writeString(tempDir.resolve("first.html"),
                "<html><body><p>First page</p></body></html>");
        Files.writeString(tempDir.resolve("second.html"),
                "<html><body><p>Second page</p></body></html>");
        new EpubPublicationDriver(tempDir)
                .publish(source(tempDir, List.of(
                        page("id-1", "https://book.example.com/first",  "first.html",  0L),
                        page("id-2", "https://book.example.com/second", "second.html", 1L))),
                        options(tempDir.resolve("out.epub")));

        final String ch1 = readEpubEntry(tempDir.resolve("out.epub"), "OEBPS/chapter-001.xhtml");
        final String ch2 = readEpubEntry(tempDir.resolve("out.epub"), "OEBPS/chapter-002.xhtml");
        assertNotNull(ch1);
        assertNotNull(ch2);
        assertTrue(ch1.contains("First page"),
                "chapter-001 must contain first page content");
        assertTrue(ch2.contains("Second page"),
                "chapter-002 must contain second page content");
    }

    // ------------------------------------------------------------------
    // Assembly report
    // ------------------------------------------------------------------

    @Test
    void publishShouldReturnCorrectChapterCount(@TempDir final Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("p1.html"), "<html><body><p>One</p></body></html>");
        Files.writeString(tempDir.resolve("p2.html"), "<html><body><p>Two</p></body></html>");

        final PublicationArtifact artifact = new EpubPublicationDriver(tempDir)
                .publish(source(tempDir, List.of(
                        page("id-1", "https://book.example.com/p1", "p1.html", 0L),
                        page("id-2", "https://book.example.com/p2", "p2.html", 1L))),
                        options(tempDir.resolve("out.epub")));

        assertEquals(2, artifact.assemblyReport().pagesAttempted());
        assertEquals(2, artifact.assemblyReport().pagesRendered());
        assertEquals(0, artifact.assemblyReport().pagesFailed());
    }

    @Test
    void publishShouldRecordFailureForMissingHtmlFile(@TempDir final Path tempDir)
            throws Exception {
        // "missing.html" intentionally not created
        final PublicationArtifact artifact = new EpubPublicationDriver(tempDir)
                .publish(source(tempDir, List.of(
                        page("id-1", "https://book.example.com/missing", "missing.html", 0L))),
                        options(tempDir.resolve("out.epub")));

        assertEquals(1, artifact.assemblyReport().pagesFailed(),
                "missing HTML file must be recorded as a failure");
        assertEquals(0, artifact.assemblyReport().pagesRendered());
    }

    @Test
    void publishShouldHandleEmptyManifestGracefully(@TempDir final Path tempDir)
            throws Exception {
        final Path outputEpub = tempDir.resolve("out.epub");
        final PublicationArtifact artifact = new EpubPublicationDriver(tempDir)
                .publish(source(tempDir, List.of()), options(outputEpub));

        assertTrue(Files.exists(outputEpub),
                "EPUB must be created even for empty manifest");
        assertEquals(0, artifact.assemblyReport().pagesAttempted());
        // must still be a valid ZIP with structural entries
        final Set<String> entries = epubEntries(outputEpub);
        assertTrue(entries.contains("mimetype"));
        assertTrue(entries.contains("OEBPS/content.opf"));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void publishOnePageEpub(final Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("page.html"),
                "<html><body><p>Content</p></body></html>");
        new EpubPublicationDriver(tempDir)
                .publish(source(tempDir, List.of(
                        page("id-1", "https://book.example.com/page", "page.html", 0L))),
                        options(tempDir.resolve("out.epub")));
    }

    private static Set<String> epubEntries(final Path epub) throws Exception {
        final Set<String> names = new HashSet<>();
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(epub))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                names.add(entry.getName());
            }
        }
        return names;
    }

    private static String readEpubEntry(final Path epub, final String entryName) throws Exception {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(epub))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().equals(entryName)) {
                    return new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        return null;
    }

    private static PublicationSource source(final Path contentDir,
            final List<MirroredPage> pages) {
        return SiteMirrorSource.of(contentDir, MirrorManifest.builder()
                .startUrl(SEED).sameDomainOnly(true).maxPages(50).maxDepth(3)
                .pages(pages).build());
    }

    private static PublicationExportOptions options(final Path outputPath) {
        return PublicationExportOptions.builder()
                .format(PublicationFormat.EPUB)
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
