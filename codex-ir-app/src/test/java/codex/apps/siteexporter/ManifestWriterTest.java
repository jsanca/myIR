package codex.apps.siteexporter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ManifestWriterTest {

    private static final URI START_URL = URI.create("https://example.com/");
    private static final Instant GENERATED_AT = Instant.parse("2026-07-03T12:00:00Z");
    private static final Instant FETCHED_AT = Instant.parse("2026-07-03T12:01:00Z");

    @Test
    void shouldWriteManifestJsonFile(@TempDir final Path tempDir) throws Exception {
        final MirrorManifest manifest = singlePageManifest();

        new ManifestWriter().write(manifest, tempDir.resolve("manifest.json"));

        assertTrue(Files.exists(tempDir.resolve("manifest.json")));
        final String json = Files.readString(tempDir.resolve("manifest.json"));
        assertTrue(json.contains("\"startUrl\""));
        assertTrue(json.contains("https://example.com/"));
        assertTrue(json.contains("\"documentCount\""));
        assertTrue(json.contains("\"successfulCount\""));
        assertTrue(json.contains("\"mirrorStatus\""));
        assertTrue(json.contains("SUCCESS"));
    }

    @Test
    void shouldRoundTripSuccessfulPage(@TempDir final Path tempDir) throws Exception {
        final MirrorManifest original = singlePageManifest();

        final Path file = tempDir.resolve("manifest.json");
        new ManifestWriter().write(original, file);
        final MirrorManifest restored = new ManifestReader().read(file);

        assertEquals(original, restored);
    }

    @Test
    void shouldRoundTripWriteFailedPage(@TempDir final Path tempDir) throws Exception {
        final MirroredPage failed = MirroredPage.builder()
                .id("id-002")
                .url(URI.create("https://example.com/shop"))
                .canonicalUrl(URI.create("https://example.com/shop"))
                .title("Shop")
                .depth(1)
                .discoveredOrder(1)
                .contentType("text/html")
                .status(200)
                .fetchedAt(FETCHED_AT)
                .mirrorStatus(MirrorStatus.WRITE_FAILED)
                .errorMessage("Permission denied")
                .build();

        final MirrorManifest original = MirrorManifest.builder()
                .startUrl(START_URL)
                .generatedAt(GENERATED_AT)
                .sameDomainOnly(true)
                .maxPages(50)
                .maxDepth(3)
                .pages(List.of(failed))
                .build();

        final Path file = tempDir.resolve("manifest.json");
        new ManifestWriter().write(original, file);
        final MirrorManifest restored = new ManifestReader().read(file);

        assertEquals(original, restored);
        assertEquals(0, restored.successfulCount());
        assertEquals(1, restored.failedCount());
        final MirroredPage restoredPage = restored.pages().get(0);
        assertEquals(MirrorStatus.WRITE_FAILED, restoredPage.mirrorStatus());
        assertEquals("Permission denied", restoredPage.errorMessage());
        assertNull(restoredPage.localHtmlPath());
    }

    @Test
    void shouldRoundTripNullableFields(@TempDir final Path tempDir) throws Exception {
        final MirroredPage page = MirroredPage.builder()
                .id("id-003")
                .url(URI.create("https://example.com/no-title"))
                .canonicalUrl(URI.create("https://example.com/no-title"))
                // title, parentUrl, contentType, fetchedAt, errorMessage all null
                .depth(0)
                .discoveredOrder(0)
                .status(200)
                .mirrorStatus(MirrorStatus.SUCCESS)
                .localHtmlPath("no-title/index.html")
                .build();

        final MirrorManifest original = MirrorManifest.builder()
                .startUrl(START_URL)
                .generatedAt(GENERATED_AT)
                .sameDomainOnly(false)
                .maxPages(10)
                .maxDepth(2)
                .pages(List.of(page))
                .build();

        final Path file = tempDir.resolve("manifest.json");
        new ManifestWriter().write(original, file);
        final MirrorManifest restored = new ManifestReader().read(file);

        assertEquals(original, restored);
        final MirroredPage restoredPage = restored.pages().get(0);
        assertNull(restoredPage.title());
        assertNull(restoredPage.parentUrl());
        assertNull(restoredPage.contentType());
        assertNull(restoredPage.fetchedAt());
        assertNull(restoredPage.errorMessage());
    }

    @Test
    void shouldRoundTripEmptyPageList(@TempDir final Path tempDir) throws Exception {
        final MirrorManifest original = MirrorManifest.builder()
                .startUrl(START_URL)
                .generatedAt(GENERATED_AT)
                .sameDomainOnly(true)
                .maxPages(100)
                .maxDepth(3)
                .pages(List.of())
                .build();

        final Path file = tempDir.resolve("manifest.json");
        new ManifestWriter().write(original, file);
        final MirrorManifest restored = new ManifestReader().read(file);

        assertEquals(original, restored);
        assertEquals(0, restored.documentCount());
    }

    @Test
    void shouldRoundTripMultiplePages(@TempDir final Path tempDir) throws Exception {
        final MirroredPage p1 = successPage("id-1", "https://example.com/", "index.html", "Home", 0);
        final MirroredPage p2 = successPage("id-2", "https://example.com/about", "about/index.html", "About", 1);
        final MirroredPage p3 = MirroredPage.builder()
                .id("id-3")
                .url(URI.create("https://example.com/bad"))
                .canonicalUrl(URI.create("https://example.com/bad"))
                .depth(1)
                .discoveredOrder(2)
                .status(200)
                .fetchedAt(FETCHED_AT)
                .mirrorStatus(MirrorStatus.WRITE_FAILED)
                .errorMessage("disk full")
                .build();

        final MirrorManifest original = MirrorManifest.builder()
                .startUrl(START_URL)
                .generatedAt(GENERATED_AT)
                .sameDomainOnly(true)
                .maxPages(10)
                .maxDepth(2)
                .pages(List.of(p1, p2, p3))
                .build();

        final Path file = tempDir.resolve("manifest.json");
        new ManifestWriter().write(original, file);
        final MirrorManifest restored = new ManifestReader().read(file);

        assertEquals(original, restored);
        assertEquals(3, restored.documentCount());
        assertEquals(2, restored.successfulCount());
        assertEquals(1, restored.failedCount());
        assertEquals(0, restored.skippedCount());
    }

    @Test
    void nullDepthShouldRoundTripAsNull(@TempDir final Path tempDir) throws Exception {
        final MirroredPage pageWithUnknownDepth = MirroredPage.builder()
                .id("id-unknown-depth")
                .url(URI.create("https://example.com/page"))
                .canonicalUrl(URI.create("https://example.com/page"))
                .localHtmlPath("page/index.html")
                .depth(null)
                .discoveredOrder(0L)
                .status(200)
                .fetchedAt(FETCHED_AT)
                .mirrorStatus(MirrorStatus.SUCCESS)
                .build();

        final MirrorManifest original = MirrorManifest.builder()
                .startUrl(START_URL)
                .generatedAt(GENERATED_AT)
                .sameDomainOnly(true)
                .maxPages(10)
                .maxDepth(2)
                .pages(List.of(pageWithUnknownDepth))
                .build();

        final Path file = tempDir.resolve("manifest.json");
        new ManifestWriter().write(original, file);
        final MirrorManifest restored = new ManifestReader().read(file);

        assertEquals(original, restored);
        assertNull(restored.pages().get(0).depth(), "null depth must survive round-trip");
    }

    @Test
    void depthZeroIsDistinctFromNullDepth(@TempDir final Path tempDir) throws Exception {
        final MirroredPage root = MirroredPage.builder()
                .id("id-root")
                .url(URI.create("https://example.com/"))
                .canonicalUrl(URI.create("https://example.com/"))
                .localHtmlPath("index.html")
                .depth(0)   // seed page — depth is known and is 0
                .discoveredOrder(0L)
                .status(200)
                .fetchedAt(FETCHED_AT)
                .mirrorStatus(MirrorStatus.SUCCESS)
                .build();

        final MirroredPage unknown = MirroredPage.builder()
                .id("id-unknown")
                .url(URI.create("https://example.com/other"))
                .canonicalUrl(URI.create("https://example.com/other"))
                .localHtmlPath("other/index.html")
                .depth(null) // depth unknown
                .discoveredOrder(1L)
                .status(200)
                .fetchedAt(FETCHED_AT)
                .mirrorStatus(MirrorStatus.SUCCESS)
                .build();

        final MirrorManifest original = MirrorManifest.builder()
                .startUrl(START_URL)
                .generatedAt(GENERATED_AT)
                .sameDomainOnly(true)
                .maxPages(10)
                .maxDepth(2)
                .pages(List.of(root, unknown))
                .build();

        final Path file = tempDir.resolve("manifest.json");
        new ManifestWriter().write(original, file);
        final MirrorManifest restored = new ManifestReader().read(file);

        assertEquals(Integer.valueOf(0), restored.pages().get(0).depth());
        assertNull(restored.pages().get(1).depth());
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static MirrorManifest singlePageManifest() {
        final MirroredPage page = successPage("id-001", "https://example.com/", "index.html", "Home", 0);
        return MirrorManifest.builder()
                .startUrl(START_URL)
                .generatedAt(GENERATED_AT)
                .sameDomainOnly(true)
                .maxPages(100)
                .maxDepth(3)
                .pages(List.of(page))
                .build();
    }

    private static MirroredPage successPage(final String id, final String url,
            final String localHtmlPath, final String title, final int order) {
        return MirroredPage.builder()
                .id(id)
                .url(URI.create(url))
                .canonicalUrl(URI.create(url))
                .localHtmlPath(localHtmlPath)
                .title(title)
                .depth(0)
                .discoveredOrder(order)
                .parentUrl(null)
                .contentType("text/html")
                .status(200)
                .fetchedAt(FETCHED_AT)
                .mirrorStatus(MirrorStatus.SUCCESS)
                .build();
    }
}
