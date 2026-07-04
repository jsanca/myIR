package codex.apps.siteexporter;

import codex.ir.ingestion.DocumentSource;
import codex.ir.ingestion.WebPage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SiteMirrorManifestSmokeTest {

    private static final URI START_URL = URI.create("https://fixture.example/");
    private static final Instant FETCHED_AT = Instant.parse("2026-07-03T12:00:00Z");

    @Test
    void shouldHonorManifestContractEndToEnd(@TempDir final Path outputDir) throws Exception {
        final SiteMirrorOptions options = SiteMirrorOptions.builder()
                .seedUrl(START_URL)
                .outputDir(outputDir)
                .maxPages(3)
                .maxDepth(2)
                .sameDomainOnly(true)
                .build();

        new SiteMirrorService().mirror(options, fixtureSiteSource());

        final Path manifestPath = outputDir.resolve(MirrorManifest.FILE_NAME);
        final MirrorManifest manifest = new ManifestReader().read(manifestPath);
        final List<MirroredPage> successfulPages = manifest.pages().stream()
                .filter(page -> page.mirrorStatus() == MirrorStatus.SUCCESS)
                .toList();

        assertTrue(Files.exists(manifestPath));
        assertEquals(3, manifest.documentCount());
        assertEquals(3, manifest.successfulCount());
        assertEquals(0, manifest.failedCount());
        assertEquals(0, manifest.skippedCount());
        assertEquals(manifest.documentCount(), manifest.pages().size());
        assertEquals(manifest.successfulCount(), successfulPages.size());

        assertEquals(List.of(0L, 1L, 2L),
                manifest.pages().stream().map(MirroredPage::discoveredOrder).toList());
        assertEquals(List.of(
                        URI.create("https://fixture.example/"),
                        URI.create("https://fixture.example/guide/"),
                        URI.create("https://fixture.example/contact.html")),
                manifest.pages().stream().map(MirroredPage::url).toList());

        for (final MirroredPage page : successfulPages) {
            assertNull(page.depth(), "unknown traversal depth must remain null");
            assertNotNull(page.localHtmlPath());

            final Path relativePath = Path.of(page.localHtmlPath());
            assertFalse(relativePath.isAbsolute(), "manifest paths must be relative");

            final Path localHtml = manifestPath.getParent().resolve(relativePath).normalize();
            assertTrue(localHtml.startsWith(manifestPath.getParent()));
            assertTrue(Files.isRegularFile(localHtml), "manifest path must resolve to an HTML file");
        }

        final String json = Files.readString(manifestPath);
        assertFalse(json.contains(outputDir.toAbsolutePath().toString()));
        assertFalse(json.contains("file:"));
    }

    private static DocumentSource<WebPage> fixtureSiteSource() {
        final List<WebPage> pages = List.of(
                fixturePage("https://fixture.example/", "index.html", "Fixture Home"),
                fixturePage("https://fixture.example/guide/", "guide.html", "Fixture Guide"),
                fixturePage("https://fixture.example/contact.html", "contact.html", "Fixture Contact"));
        return consumer -> pages.forEach(consumer);
    }

    private static WebPage fixturePage(final String url, final String resource, final String title) {
        return new WebPage(URI.create(url), readFixture(resource), title, title,
                200, "text/html", FETCHED_AT, Map.of());
    }

    private static String readFixture(final String name) {
        final String resource = "/fixtures/site-mirror/" + name;
        try (InputStream input = SiteMirrorManifestSmokeTest.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalStateException("Missing fixture: " + resource);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (final IOException e) {
            throw new IllegalStateException("Unable to read fixture: " + resource, e);
        }
    }
}
