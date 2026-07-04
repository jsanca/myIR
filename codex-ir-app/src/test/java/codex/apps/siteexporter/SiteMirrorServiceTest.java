package codex.apps.siteexporter;

import codex.ir.ingestion.WebPage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SiteMirrorServiceTest {

    private static final URI SEED = URI.create("https://example.com/");

    @Test
    void shouldWriteEachCrawledPageToOutputDir(@TempDir final Path tempDir) throws Exception {
        final List<WebPage> pages = List.of(
                page("https://example.com/", "<html>Home</html>", "Home"),
                page("https://example.com/about", "<html>About</html>", "About"),
                page("https://example.com/products/", "<html>Products</html>", "Products"));

        final MirrorManifest manifest = mirror(tempDir, pages);

        assertTrue(Files.exists(tempDir.resolve("index.html")));
        assertTrue(Files.exists(tempDir.resolve("about/index.html")));
        assertTrue(Files.exists(tempDir.resolve("products/index.html")));
        assertEquals("<html>Home</html>", Files.readString(tempDir.resolve("index.html")));
        assertEquals("<html>About</html>", Files.readString(tempDir.resolve("about/index.html")));
        assertEquals(3, manifest.pages().size());
        assertEquals(3, manifest.successfulCount());
    }

    @Test
    void shouldWriteManifestJsonToOutputDir(@TempDir final Path tempDir) throws Exception {
        final List<WebPage> pages = List.of(page("https://example.com/", "<html>Home</html>", "Home"));

        mirror(tempDir, pages);

        final Path manifestPath = tempDir.resolve(MirrorManifest.FILE_NAME);
        assertTrue(Files.exists(manifestPath));

        final String json = Files.readString(manifestPath);
        assertTrue(json.contains("\"startUrl\""));
        assertTrue(json.contains("\"generatedAt\""));
        assertTrue(json.contains("\"documentCount\""));
        assertTrue(json.contains("\"successfulCount\""));
        assertTrue(json.contains("https://example.com/"));
    }

    @Test
    void shouldRecordCorrectMetadataInManifestEntries(@TempDir final Path tempDir) throws Exception {
        final Instant fetchedAt = Instant.parse("2026-07-03T00:00:00Z");
        final WebPage page = new WebPage(
                URI.create("https://example.com/shop"),
                "<html>Shop</html>", "Shop", "Shop body",
                200, "text/html", fetchedAt, Map.of());

        final MirrorManifest manifest = mirror(tempDir, List.of(page));

        assertEquals(1, manifest.pages().size());
        final MirroredPage entry = manifest.pages().get(0);
        assertEquals(URI.create("https://example.com/shop"), entry.url());
        assertEquals("Shop", entry.title());
        assertEquals(200, entry.status());
        assertEquals(fetchedAt, entry.fetchedAt());
        assertEquals(MirrorStatus.SUCCESS, entry.mirrorStatus());
        assertNotNull(entry.id());
        assertNotNull(entry.localHtmlPath());
        assertFalse(entry.localHtmlPath().startsWith("/"), "localHtmlPath must be relative");
        assertNull(entry.depth(), "depth must be null when crawler does not expose traversal depth");
    }

    @Test
    void shouldProduceEmptyManifestWhenNoPagesCrawled(@TempDir final Path tempDir) throws Exception {
        final MirrorManifest manifest = mirror(tempDir, List.of());

        assertEquals(0, manifest.documentCount());
        assertEquals(0, manifest.successfulCount());
        assertEquals(SEED, manifest.startUrl());
        assertTrue(Files.exists(tempDir.resolve(MirrorManifest.FILE_NAME)));
    }

    @Test
    void shouldThrowWhenOutputDirExistsAsFile(@TempDir final Path tempDir) throws Exception {
        final Path file = tempDir.resolve("not-a-dir");
        Files.createFile(file);

        final SiteMirrorOptions options = options(file);
        final SiteMirrorService service = new SiteMirrorService();

        boolean threw = false;
        try {
            service.mirror(options, consumer -> {});
        } catch (final IllegalArgumentException e) {
            threw = true;
            assertTrue(e.getMessage().contains("not a directory"));
        }
        assertTrue(threw, "Expected IllegalArgumentException for outputDir that is a file");
    }

    @Test
    void shouldRespectSameDomainOnlyOptionInConfigBuilding(@TempDir final Path tempDir) throws Exception {
        final SiteMirrorOptions options = SiteMirrorOptions.builder()
                .seedUrl(SEED)
                .outputDir(tempDir)
                .maxPages(5)
                .maxDepth(1)
                .sameDomainOnly(true)
                .build();

        assertTrue(options.sameDomainOnly(), "Same-domain must default to true");
    }

    @Test
    void shouldRecordWriteFailedWhenFileCannotBeWritten(@TempDir final Path tempDir) throws Exception {
        // Block 'about/index.html' by creating a file at 'about' so createDirectories fails
        Files.createFile(tempDir.resolve("about"));

        final List<WebPage> pages = List.of(
                page("https://example.com/", "<html>Home</html>", "Home"),
                page("https://example.com/about", "<html>About</html>", "About"));

        final MirrorManifest manifest = mirror(tempDir, pages);

        assertEquals(2, manifest.documentCount());
        assertEquals(1, manifest.successfulCount());
        assertEquals(1, manifest.failedCount());

        final MirroredPage failed = manifest.pages().stream()
                .filter(p -> p.mirrorStatus() == MirrorStatus.WRITE_FAILED)
                .findFirst()
                .orElseThrow();
        assertEquals(URI.create("https://example.com/about"), failed.url());
        assertNull(failed.localHtmlPath());
        assertNotNull(failed.errorMessage());
    }

    @Test
    void manifestPathsShouldBeRelativeToOutputDir(@TempDir final Path tempDir) throws Exception {
        final List<WebPage> pages = List.of(
                page("https://example.com/section/page", "<html>Page</html>", "Page"));

        final MirrorManifest manifest = mirror(tempDir, pages);

        final MirroredPage entry = manifest.pages().get(0);
        assertEquals(MirrorStatus.SUCCESS, entry.mirrorStatus());
        assertEquals("section/page/index.html", entry.localHtmlPath());
    }

    @Test
    void manifestShouldIncludeConfigMetadata(@TempDir final Path tempDir) throws Exception {
        final SiteMirrorOptions options = SiteMirrorOptions.builder()
                .seedUrl(SEED)
                .outputDir(tempDir)
                .maxPages(42)
                .maxDepth(5)
                .sameDomainOnly(false)
                .build();
        final SiteMirrorService service = new SiteMirrorService();
        final MirrorManifest manifest = service.mirror(options, consumer -> {});

        assertEquals(42, manifest.maxPages());
        assertEquals(5, manifest.maxDepth());
        assertFalse(manifest.sameDomainOnly());
        assertEquals(MirrorManifest.MANIFEST_VERSION, manifest.manifestVersion());
        assertNotNull(manifest.generatedAt());
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static MirrorManifest mirror(final Path outputDir, final List<WebPage> pages) throws Exception {
        final SiteMirrorOptions options = options(outputDir);
        final SiteMirrorService service = new SiteMirrorService();
        return service.mirror(options, consumer -> pages.forEach(consumer));
    }

    private static SiteMirrorOptions options(final Path outputDir) {
        return SiteMirrorOptions.builder()
                .seedUrl(SEED)
                .outputDir(outputDir)
                .maxPages(10)
                .maxDepth(2)
                .sameDomainOnly(true)
                .build();
    }

    private static WebPage page(final String url, final String html, final String title) {
        return new WebPage(URI.create(url), html, title, "body",
                200, "text/html", Instant.now(), Map.of());
    }
}
