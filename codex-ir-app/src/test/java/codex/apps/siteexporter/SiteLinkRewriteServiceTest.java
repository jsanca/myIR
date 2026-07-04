package codex.apps.siteexporter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SiteLinkRewriteServiceTest {

    private static final URI SEED = URI.create("https://example.com/");
    private static final Instant FETCHED_AT = Instant.parse("2026-07-03T12:00:00Z");

    @Test
    void shouldRewriteInternalLinkToRelativePath(@TempDir final Path tempDir) throws Exception {
        writeHtml(tempDir, "index.html",
                "<html><body><a href=\"https://example.com/about\">About</a></body></html>");
        writeHtml(tempDir, "about/index.html", "<html><body>About page</body></html>");

        final MirrorManifest mirror = mirrorManifest(
                successPage("id-1", "https://example.com/", "index.html"),
                successPage("id-2", "https://example.com/about", "about/index.html"));

        new SiteLinkRewriteService().rewrite(tempDir, mirror, emptyAssets());

        final String rewritten = Files.readString(tempDir.resolve("index.html"));
        assertTrue(rewritten.contains("about/index.html"), "link must be rewritten to local path");
        assertFalse(rewritten.contains("https://example.com/about"), "absolute URL must be removed");
    }

    @Test
    void shouldLeaveExternalLinksUnchanged(@TempDir final Path tempDir) throws Exception {
        writeHtml(tempDir, "index.html",
                "<html><body><a href=\"https://external.org/\">External</a></body></html>");

        final MirrorManifest mirror = mirrorManifest(
                successPage("id-1", "https://example.com/", "index.html"));

        new SiteLinkRewriteService().rewrite(tempDir, mirror, emptyAssets());

        final String rewritten = Files.readString(tempDir.resolve("index.html"));
        assertTrue(rewritten.contains("https://external.org/"), "external link must remain");
    }

    @Test
    void shouldRewriteAssetReferenceToLocalPath(@TempDir final Path tempDir) throws Exception {
        writeHtml(tempDir, "index.html",
                "<html><body><img src=\"https://example.com/img/logo.png\"></body></html>");
        Files.createDirectories(tempDir.resolve("assets/img"));
        Files.write(tempDir.resolve("assets/img/logo.png"), new byte[]{1, 2, 3});

        final MirrorManifest mirror = mirrorManifest(
                successPage("id-1", "https://example.com/", "index.html"));
        final AssetManifest assets = assetManifest(
                successAsset("https://example.com/img/logo.png", "assets/img/logo.png"));

        new SiteLinkRewriteService().rewrite(tempDir, mirror, assets);

        final String rewritten = Files.readString(tempDir.resolve("index.html"));
        assertTrue(rewritten.contains("assets/img/logo.png"), "img src must be rewritten");
    }

    @Test
    void shouldRewriteStylesheetAndScriptLinks(@TempDir final Path tempDir) throws Exception {
        final String html = """
                <html>
                <head>
                  <link rel="stylesheet" href="https://example.com/css/main.css">
                  <script src="https://example.com/js/app.js"></script>
                </head>
                <body></body>
                </html>
                """;
        writeHtml(tempDir, "index.html", html);

        final MirrorManifest mirror = mirrorManifest(
                successPage("id-1", "https://example.com/", "index.html"));
        final AssetManifest assets = assetManifest(
                successAsset("https://example.com/css/main.css", "assets/css/main.css"),
                successAsset("https://example.com/js/app.js", "assets/js/app.js"));

        new SiteLinkRewriteService().rewrite(tempDir, mirror, assets);

        final String rewritten = Files.readString(tempDir.resolve("index.html"));
        assertTrue(rewritten.contains("assets/css/main.css"), "stylesheet must be rewritten");
        assertTrue(rewritten.contains("assets/js/app.js"), "script must be rewritten");
    }

    @Test
    void shouldUseCorrectRelativePathFromNestedPage(@TempDir final Path tempDir) throws Exception {
        writeHtml(tempDir, "section/page/index.html",
                "<html><body><a href=\"https://example.com/about\">About</a></body></html>");
        writeHtml(tempDir, "about/index.html", "<html><body>About</body></html>");

        final MirrorManifest mirror = mirrorManifest(
                successPage("id-1", "https://example.com/section/page", "section/page/index.html"),
                successPage("id-2", "https://example.com/about", "about/index.html"));

        new SiteLinkRewriteService().rewrite(tempDir, mirror, emptyAssets());

        final String rewritten = Files.readString(tempDir.resolve("section/page/index.html"));
        assertTrue(rewritten.contains("../../about/index.html"), "nested page must use ../ to navigate up");
    }

    @Test
    void shouldSkipWriteFailedPages(@TempDir final Path tempDir) throws Exception {
        writeHtml(tempDir, "index.html", "<html><body>Home</body></html>");

        final MirroredPage failed = MirroredPage.builder()
                .id("id-fail")
                .url(URI.create("https://example.com/bad"))
                .canonicalUrl(URI.create("https://example.com/bad"))
                .depth(0).discoveredOrder(1L).status(200).fetchedAt(FETCHED_AT)
                .mirrorStatus(MirrorStatus.WRITE_FAILED).errorMessage("disk full")
                .build();
        final MirrorManifest mirror = mirrorManifest(
                successPage("id-1", "https://example.com/", "index.html"), failed);

        final int count = new SiteLinkRewriteService().rewrite(tempDir, mirror, emptyAssets());

        assertEquals(1, count, "only SUCCESS pages should be rewritten");
    }

    @Test
    void shouldReturnCountOfRewrittenPages(@TempDir final Path tempDir) throws Exception {
        writeHtml(tempDir, "index.html", "<html><body>Home</body></html>");
        writeHtml(tempDir, "about/index.html", "<html><body>About</body></html>");
        writeHtml(tempDir, "contact/index.html", "<html><body>Contact</body></html>");

        final MirrorManifest mirror = mirrorManifest(
                successPage("id-1", "https://example.com/", "index.html"),
                successPage("id-2", "https://example.com/about", "about/index.html"),
                successPage("id-3", "https://example.com/contact", "contact/index.html"));

        final int count = new SiteLinkRewriteService().rewrite(tempDir, mirror, emptyAssets());

        assertEquals(3, count);
    }

    @Test
    void shouldNotModifyHtmlWithNoRewritableLinks(@TempDir final Path tempDir) throws Exception {
        final String original = "<html><body><p>No links here</p></body></html>";
        writeHtml(tempDir, "index.html", original);

        final MirrorManifest mirror = mirrorManifest(
                successPage("id-1", "https://example.com/", "index.html"));

        new SiteLinkRewriteService().rewrite(tempDir, mirror, emptyAssets());

        final String rewritten = Files.readString(tempDir.resolve("index.html"));
        assertTrue(rewritten.contains("No links here"), "page content must be preserved");
    }

    @Test
    void shouldLeaveUndownloadedAssetUnchanged(@TempDir final Path tempDir) throws Exception {
        writeHtml(tempDir, "index.html",
                "<html><body><img src=\"https://example.com/img/missing.png\"></body></html>");

        final MirrorManifest mirror = mirrorManifest(
                successPage("id-1", "https://example.com/", "index.html"));

        new SiteLinkRewriteService().rewrite(tempDir, mirror, emptyAssets());

        final String rewritten = Files.readString(tempDir.resolve("index.html"));
        assertTrue(rewritten.contains("https://example.com/img/missing.png"),
                "undownloaded asset reference must remain unchanged");
    }

    @Test
    void pagesBothLinkedToEachOtherShouldHaveBidirectionalRelativePaths(@TempDir final Path tempDir)
            throws Exception {
        writeHtml(tempDir, "index.html",
                "<html><body><a href=\"https://example.com/about\">About</a></body></html>");
        writeHtml(tempDir, "about/index.html",
                "<html><body><a href=\"https://example.com/\">Home</a></body></html>");

        final MirrorManifest mirror = mirrorManifest(
                successPage("id-1", "https://example.com/", "index.html"),
                successPage("id-2", "https://example.com/about", "about/index.html"));

        new SiteLinkRewriteService().rewrite(tempDir, mirror, emptyAssets());

        final String indexRewritten = Files.readString(tempDir.resolve("index.html"));
        final String aboutRewritten = Files.readString(tempDir.resolve("about/index.html"));

        assertTrue(indexRewritten.contains("about/index.html"), "index → about path correct");
        assertTrue(aboutRewritten.contains("../index.html"), "about → index path correct");
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static void writeHtml(final Path outputDir, final String relativePath, final String html)
            throws Exception {
        final Path file = outputDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, html);
    }

    @SafeVarargs
    private static MirrorManifest mirrorManifest(final MirroredPage... pages) {
        return MirrorManifest.builder()
                .startUrl(SEED).sameDomainOnly(true).maxPages(10).maxDepth(2)
                .pages(List.of(pages)).build();
    }

    private static AssetManifest emptyAssets() {
        return AssetManifest.builder().assets(List.of()).build();
    }

    private static AssetManifest assetManifest(final AssetMetadata... assets) {
        return AssetManifest.builder().assets(List.of(assets)).build();
    }

    private static MirroredPage successPage(final String id, final String url,
            final String localHtmlPath) {
        return MirroredPage.builder()
                .id(id).url(URI.create(url)).canonicalUrl(URI.create(url))
                .localHtmlPath(localHtmlPath).depth(0).discoveredOrder(0L)
                .status(200).fetchedAt(FETCHED_AT).mirrorStatus(MirrorStatus.SUCCESS).build();
    }

    private static AssetMetadata successAsset(final String url, final String localAssetPath) {
        return AssetMetadata.builder()
                .id("asset").url(URI.create(url)).assetType(AssetType.IMAGE)
                .localAssetPath(localAssetPath).statusCode(200).assetStatus(AssetStatus.SUCCESS).build();
    }
}
