package codex.apps.siteexporter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SiteAssetServiceTest {

    private static final URI SEED = URI.create("https://example.com/");
    private static final Instant FETCHED_AT = Instant.parse("2026-07-03T12:00:00Z");

    // ------------------------------------------------------------------
    // Stub fetcher helpers
    // ------------------------------------------------------------------

    private static AssetFetcher successFetcher(final byte[] body) {
        return url -> new AssetFetcher.Result(200, "image/png", body);
    }

    private static AssetFetcher failFetcher(final int statusCode) {
        return url -> new AssetFetcher.Result(statusCode, null, new byte[0]);
    }

    private static AssetFetcher errorFetcher() {
        return url -> { throw new java.io.IOException("connection refused"); };
    }

    // ------------------------------------------------------------------
    // Tests
    // ------------------------------------------------------------------

    @Test
    void shouldDownloadImageAndRecordSuccess(@TempDir final Path tempDir) throws Exception {
        writeHtml(tempDir, "index.html", "<html><body><img src=\"/img/logo.png\"></body></html>");
        final MirrorManifest mirror = singlePageManifest(tempDir, "https://example.com/", "index.html");

        final AssetManifest manifest = service().download(options(tempDir), mirror, successFetcher(new byte[]{1, 2, 3}));

        assertEquals(1, manifest.totalCount());
        assertEquals(1, manifest.successfulCount());
        assertEquals(0, manifest.failedCount());
        final AssetMetadata asset = manifest.assets().get(0);
        assertEquals(AssetStatus.SUCCESS, asset.assetStatus());
        assertEquals(URI.create("https://example.com/img/logo.png"), asset.url());
        assertEquals(AssetType.IMAGE, asset.assetType());
        assertNotNull(asset.localAssetPath());
        assertTrue(asset.localAssetPath().startsWith("assets/"), "path must start with assets/");
        assertTrue(Files.exists(tempDir.resolve(asset.localAssetPath())));
    }

    @Test
    void shouldRecordDownloadFailedOnHttpError(@TempDir final Path tempDir) throws Exception {
        writeHtml(tempDir, "index.html", "<html><body><img src=\"/missing.png\"></body></html>");
        final MirrorManifest mirror = singlePageManifest(tempDir, "https://example.com/", "index.html");

        final AssetManifest manifest = service().download(options(tempDir), mirror, failFetcher(404));

        assertEquals(1, manifest.failedCount());
        final AssetMetadata asset = manifest.assets().get(0);
        assertEquals(AssetStatus.DOWNLOAD_FAILED, asset.assetStatus());
        assertNull(asset.localAssetPath());
        assertNotNull(asset.errorMessage());
    }

    @Test
    void shouldRecordDownloadFailedOnNetworkError(@TempDir final Path tempDir) throws Exception {
        writeHtml(tempDir, "index.html", "<html><body><img src=\"/img.png\"></body></html>");
        final MirrorManifest mirror = singlePageManifest(tempDir, "https://example.com/", "index.html");

        final AssetManifest manifest = service().download(options(tempDir), mirror, errorFetcher());

        assertEquals(1, manifest.failedCount());
        assertEquals("connection refused", manifest.assets().get(0).errorMessage());
    }

    @Test
    void shouldSkipExternalAssetWhenSameDomainOnlyIsTrue(@TempDir final Path tempDir) throws Exception {
        writeHtml(tempDir, "index.html",
                "<html><body><img src=\"https://cdn.other.com/logo.png\"></body></html>");
        final MirrorManifest mirror = singlePageManifest(tempDir, "https://example.com/", "index.html");

        final AssetManifest manifest = service().download(options(tempDir), mirror, successFetcher(new byte[0]));

        assertEquals(1, manifest.skippedCount());
        assertEquals(AssetStatus.SKIPPED, manifest.assets().get(0).assetStatus());
    }

    @Test
    void shouldNotSkipExternalAssetWhenSameDomainOnlyIsFalse(@TempDir final Path tempDir) throws Exception {
        writeHtml(tempDir, "index.html",
                "<html><body><img src=\"https://cdn.other.com/logo.png\"></body></html>");
        final MirrorManifest mirror = singlePageManifest(tempDir, "https://example.com/", "index.html");

        final SiteMirrorOptions opts = SiteMirrorOptions.builder()
                .seedUrl(SEED).outputDir(tempDir).maxPages(10).maxDepth(2).sameDomainOnly(false).build();
        final AssetManifest manifest = service().download(opts, mirror, successFetcher(new byte[]{42}));

        assertEquals(1, manifest.successfulCount());
    }

    @Test
    void shouldDeduplicateIdenticalAssetAcrossPages(@TempDir final Path tempDir) throws Exception {
        writeHtml(tempDir, "index.html", "<html><body><img src=\"/img/logo.png\"></body></html>");
        writeHtml(tempDir, "about/index.html", "<html><body><img src=\"/img/logo.png\"></body></html>");

        final List<MirroredPage> pages = List.of(
                successPage("id-1", "https://example.com/", "index.html"),
                successPage("id-2", "https://example.com/about", "about/index.html"));
        final MirrorManifest mirror = manifestWithPages(tempDir, pages);

        final AssetManifest manifest = service().download(options(tempDir), mirror, successFetcher(new byte[]{1}));

        assertEquals(1, manifest.totalCount(), "identical asset across two pages counts once");
    }

    @Test
    void shouldExtractMultipleAssetTypesFromSinglePage(@TempDir final Path tempDir) throws Exception {
        final String html = """
                <html>
                <head>
                  <link rel="stylesheet" href="/css/main.css">
                  <script src="/js/app.js"></script>
                </head>
                <body>
                  <img src="/img/hero.png">
                </body>
                </html>
                """;
        writeHtml(tempDir, "index.html", html);
        final MirrorManifest mirror = singlePageManifest(tempDir, "https://example.com/", "index.html");

        final AssetManifest manifest = service().download(options(tempDir), mirror, successFetcher(new byte[]{0}));

        assertEquals(3, manifest.totalCount());
        assertEquals(3, manifest.successfulCount());
        final boolean hasImage = manifest.assets().stream().anyMatch(a -> a.assetType() == AssetType.IMAGE);
        final boolean hasCss = manifest.assets().stream().anyMatch(a -> a.assetType() == AssetType.STYLESHEET);
        final boolean hasJs = manifest.assets().stream().anyMatch(a -> a.assetType() == AssetType.SCRIPT);
        assertTrue(hasImage && hasCss && hasJs);
    }

    @Test
    void shouldWriteAssetManifestJsonToOutputDir(@TempDir final Path tempDir) throws Exception {
        writeHtml(tempDir, "index.html", "<html><body><img src=\"/img/logo.png\"></body></html>");
        final MirrorManifest mirror = singlePageManifest(tempDir, "https://example.com/", "index.html");

        service().download(options(tempDir), mirror, successFetcher(new byte[]{1}));

        final Path manifestFile = tempDir.resolve(AssetManifest.FILE_NAME);
        assertTrue(Files.exists(manifestFile));
        final String json = Files.readString(manifestFile);
        assertTrue(json.contains("\"totalCount\""));
        assertTrue(json.contains("\"successfulCount\""));
        assertTrue(json.contains("\"assetStatus\""));
    }

    @Test
    void assetManifestShouldRoundTripThroughJson(@TempDir final Path tempDir) throws Exception {
        writeHtml(tempDir, "index.html", "<html><body><img src=\"/img/logo.png\"></body></html>");
        final MirrorManifest mirror = singlePageManifest(tempDir, "https://example.com/", "index.html");

        final AssetManifest original = service().download(options(tempDir), mirror, successFetcher(new byte[]{1}));
        final AssetManifest restored = AssetManifest.readFrom(tempDir);

        assertEquals(original.totalCount(), restored.totalCount());
        assertEquals(original.successfulCount(), restored.successfulCount());
        assertEquals(original.assets().get(0).url(), restored.assets().get(0).url());
        assertEquals(original.assets().get(0).assetStatus(), restored.assets().get(0).assetStatus());
    }

    @Test
    void shouldProduceEmptyManifestWhenNoAssetsReferenced(@TempDir final Path tempDir) throws Exception {
        writeHtml(tempDir, "index.html", "<html><body><p>No assets here</p></body></html>");
        final MirrorManifest mirror = singlePageManifest(tempDir, "https://example.com/", "index.html");

        final AssetManifest manifest = service().download(options(tempDir), mirror, successFetcher(new byte[0]));

        assertEquals(0, manifest.totalCount());
    }

    @Test
    void shouldSkipWriteFailedPagesWhenExtractingAssets(@TempDir final Path tempDir) throws Exception {
        final MirroredPage failed = MirroredPage.builder()
                .id("id-fail")
                .url(URI.create("https://example.com/bad"))
                .canonicalUrl(URI.create("https://example.com/bad"))
                .depth(0).discoveredOrder(0L).status(200).fetchedAt(FETCHED_AT)
                .mirrorStatus(MirrorStatus.WRITE_FAILED)
                .errorMessage("disk full")
                .build();
        final MirrorManifest mirror = manifestWithPages(tempDir, List.of(failed));

        final AssetManifest manifest = service().download(options(tempDir), mirror, successFetcher(new byte[0]));

        assertEquals(0, manifest.totalCount(), "WRITE_FAILED pages must be ignored");
    }

    @Test
    void localAssetPathMustBeRelativeToOutputDir(@TempDir final Path tempDir) throws Exception {
        writeHtml(tempDir, "index.html", "<html><body><img src=\"/img/photo.jpg\"></body></html>");
        final MirrorManifest mirror = singlePageManifest(tempDir, "https://example.com/", "index.html");

        final AssetManifest manifest = service().download(options(tempDir), mirror, successFetcher(new byte[]{1}));

        final AssetMetadata asset = manifest.assets().get(0);
        assertEquals(AssetStatus.SUCCESS, asset.assetStatus());
        assertFalse(asset.localAssetPath().startsWith("/"), "localAssetPath must be relative, not absolute");
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static SiteAssetService service() {
        return new SiteAssetService();
    }

    private static SiteMirrorOptions options(final Path outputDir) {
        return SiteMirrorOptions.builder()
                .seedUrl(SEED).outputDir(outputDir).maxPages(10).maxDepth(2).sameDomainOnly(true).build();
    }

    private static void writeHtml(final Path outputDir, final String relativePath, final String html)
            throws Exception {
        final Path file = outputDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, html);
    }

    private static MirrorManifest singlePageManifest(final Path outputDir,
            final String url, final String localHtmlPath) {
        return manifestWithPages(outputDir, List.of(successPage("id-1", url, localHtmlPath)));
    }

    private static MirrorManifest manifestWithPages(final Path outputDir,
            final List<MirroredPage> pages) {
        return MirrorManifest.builder()
                .startUrl(SEED)
                .sameDomainOnly(true)
                .maxPages(10)
                .maxDepth(2)
                .pages(pages)
                .build();
    }

    private static MirroredPage successPage(final String id, final String url,
            final String localHtmlPath) {
        return MirroredPage.builder()
                .id(id)
                .url(URI.create(url))
                .canonicalUrl(URI.create(url))
                .localHtmlPath(localHtmlPath)
                .depth(0).discoveredOrder(0L).status(200).fetchedAt(FETCHED_AT)
                .mirrorStatus(MirrorStatus.SUCCESS)
                .build();
    }
}
