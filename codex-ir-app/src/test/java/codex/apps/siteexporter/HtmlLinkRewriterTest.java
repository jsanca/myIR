package codex.apps.siteexporter;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HtmlLinkRewriterTest {

    private static final URI BASE = URI.create("https://example.com/");
    private static final Instant FETCHED_AT = Instant.parse("2026-07-03T12:00:00Z");

    private final HtmlLinkRewriter rewriter = new HtmlLinkRewriter();

    // ------------------------------------------------------------------
    // Page link rewriting
    // ------------------------------------------------------------------

    @Test
    void shouldRewriteInternalPageLinkToRelativePath() {
        final PageLinkRewritePlan pagePlan = planWithPages(
                "https://example.com/about", "about/index.html");
        final String html = "<html><body><a href=\"https://example.com/about\">About</a></body></html>";

        final String result = rewriter.rewrite(html, BASE, "index.html", pagePlan, emptyAssets());

        assertTrue(result.contains("about/index.html"), "link must point to local path");
        assertFalse(result.contains("https://example.com/about"), "absolute URL must be removed");
    }

    @Test
    void shouldLeaveExternalLinkUnchanged() {
        final String html = "<html><body><a href=\"https://external.org/page\">External</a></body></html>";

        final String result = rewriter.rewrite(html, BASE, "index.html", emptyPages(), emptyAssets());

        assertTrue(result.contains("https://external.org/page"), "external link must remain");
    }

    @Test
    void shouldLeaveUnmirroredInternalLinkUnchanged() {
        final String html = "<html><body><a href=\"https://example.com/not-mirrored\">Page</a></body></html>";

        final String result = rewriter.rewrite(html, BASE, "index.html", emptyPages(), emptyAssets());

        assertTrue(result.contains("https://example.com/not-mirrored"), "unmirrored link must remain");
    }

    @Test
    void shouldLeaveFragmentLinkUnchanged() {
        final String html = "<html><body><a href=\"#section\">Section</a></body></html>";

        final String result = rewriter.rewrite(html, BASE, "index.html", emptyPages(), emptyAssets());

        assertTrue(result.contains("#section"), "fragment link must remain");
    }

    @Test
    void shouldComputeRelativePathFromNestedPage() {
        final PageLinkRewritePlan pagePlan = planWithPages(
                "https://example.com/about", "about/index.html");
        final String html = "<html><body><a href=\"https://example.com/about\">About</a></body></html>";

        // page is at section/page/index.html — needs ../../about/index.html
        final String result = rewriter.rewrite(html, URI.create("https://example.com/section/page"),
                "section/page/index.html", pagePlan, emptyAssets());

        assertTrue(result.contains("../../about/index.html"), "relative path must traverse up");
    }

    @Test
    void shouldRewriteSiblingPageRelatively() {
        final PageLinkRewritePlan pagePlan = planWithPages(
                "https://example.com/section/other", "section/other/index.html");
        final String html = "<html><body><a href=\"https://example.com/section/other\">Other</a></body></html>";

        final String result = rewriter.rewrite(html, URI.create("https://example.com/section/page"),
                "section/page/index.html", pagePlan, emptyAssets());

        assertTrue(result.contains("../other/index.html"), "sibling relative path must be correct");
    }

    @Test
    void shouldRewriteMultipleLinksInOneDocument() {
        final PageLinkRewritePlan pagePlan = planWithPages(
                "https://example.com/about", "about/index.html",
                "https://example.com/contact", "contact/index.html");
        final String html = """
                <html><body>
                  <a href="https://example.com/about">About</a>
                  <a href="https://example.com/contact">Contact</a>
                </body></html>
                """;

        final String result = rewriter.rewrite(html, BASE, "index.html", pagePlan, emptyAssets());

        assertTrue(result.contains("about/index.html"));
        assertTrue(result.contains("contact/index.html"));
    }

    // ------------------------------------------------------------------
    // Asset link rewriting
    // ------------------------------------------------------------------

    @Test
    void shouldRewriteImgSrcToLocalAssetPath() {
        final AssetLinkRewritePlan assetPlan = planWithAssets(
                "https://example.com/img/logo.png", "assets/img/logo.png");
        final String html = "<html><body><img src=\"https://example.com/img/logo.png\"></body></html>";

        final String result = rewriter.rewrite(html, BASE, "index.html", emptyPages(), assetPlan);

        assertTrue(result.contains("assets/img/logo.png"), "img src must point to local asset");
    }

    @Test
    void shouldRewriteStylesheetLinkHref() {
        final AssetLinkRewritePlan assetPlan = planWithAssets(
                "https://example.com/css/style.css", "assets/css/style.css");
        final String html = "<html><head><link rel=\"stylesheet\" href=\"https://example.com/css/style.css\"></head></html>";

        final String result = rewriter.rewrite(html, BASE, "index.html", emptyPages(), assetPlan);

        assertTrue(result.contains("assets/css/style.css"), "stylesheet link must point to local asset");
    }

    @Test
    void shouldRewriteScriptSrc() {
        final AssetLinkRewritePlan assetPlan = planWithAssets(
                "https://example.com/js/app.js", "assets/js/app.js");
        final String html = "<html><head><script src=\"https://example.com/js/app.js\"></script></head></html>";

        final String result = rewriter.rewrite(html, BASE, "index.html", emptyPages(), assetPlan);

        assertTrue(result.contains("assets/js/app.js"), "script src must point to local asset");
    }

    @Test
    void shouldLeaveUndownloadedAssetReferenceUnchanged() {
        final String html = "<html><body><img src=\"https://example.com/img/missing.png\"></body></html>";

        final String result = rewriter.rewrite(html, BASE, "index.html", emptyPages(), emptyAssets());

        assertTrue(result.contains("https://example.com/img/missing.png"), "undownloaded asset must remain");
    }

    @Test
    void shouldComputeRelativePathFromNestedPageToAsset() {
        final AssetLinkRewritePlan assetPlan = planWithAssets(
                "https://example.com/img/logo.png", "assets/img/logo.png");
        final String html = "<html><body><img src=\"https://example.com/img/logo.png\"></body></html>";

        final String result = rewriter.rewrite(html, URI.create("https://example.com/section/page"),
                "section/page/index.html", emptyPages(), assetPlan);

        assertTrue(result.contains("../../assets/img/logo.png"), "relative path to asset must traverse up");
    }

    // ------------------------------------------------------------------
    // computeRelativePath unit tests
    // ------------------------------------------------------------------

    @Test
    void relativePathFromRootPageToTopLevelTarget() {
        assertEquals("about/index.html",
                HtmlLinkRewriter.computeRelativePath("index.html", "about/index.html"));
    }

    @Test
    void relativePathFromNestedPageToTopLevel() {
        assertEquals("../../about/index.html",
                HtmlLinkRewriter.computeRelativePath("section/page/index.html", "about/index.html"));
    }

    @Test
    void relativePathFromNestedPageToAsset() {
        assertEquals("../../assets/img/logo.png",
                HtmlLinkRewriter.computeRelativePath("section/page/index.html", "assets/img/logo.png"));
    }

    @Test
    void relativePathToSiblingPage() {
        assertEquals("../other/index.html",
                HtmlLinkRewriter.computeRelativePath("section/page/index.html", "section/other/index.html"));
    }

    @Test
    void relativePathFromRootPageToAsset() {
        assertEquals("assets/css/style.css",
                HtmlLinkRewriter.computeRelativePath("index.html", "assets/css/style.css"));
    }

    // ------------------------------------------------------------------
    // Jsoup round-trip integrity
    // ------------------------------------------------------------------

    @Test
    void rewrittenHtmlShouldContainOriginalContent() {
        final PageLinkRewritePlan pagePlan = planWithPages(
                "https://example.com/about", "about/index.html");
        final String html = """
                <!DOCTYPE html>
                <html>
                <head><title>Home</title></head>
                <body>
                  <h1>Hello</h1>
                  <a href="https://example.com/about">About</a>
                  <p>Some text</p>
                </body>
                </html>
                """;

        final String result = rewriter.rewrite(html, BASE, "index.html", pagePlan, emptyAssets());

        assertTrue(result.contains("Hello"), "text content must be preserved");
        assertTrue(result.contains("Some text"), "paragraph must be preserved");
        assertTrue(result.contains("Home"), "title must be preserved");
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static PageLinkRewritePlan emptyPages() {
        return PageLinkRewritePlan.from(MirrorManifest.builder()
                .startUrl(BASE).sameDomainOnly(true).maxPages(10).maxDepth(2).pages(List.of()).build());
    }

    private static AssetLinkRewritePlan emptyAssets() {
        return AssetLinkRewritePlan.from(AssetManifest.builder().assets(List.of()).build());
    }

    private static PageLinkRewritePlan planWithPages(final String... urlLocalPathPairs) {
        final java.util.List<MirroredPage> pages = new java.util.ArrayList<>();
        for (int i = 0; i < urlLocalPathPairs.length; i += 2) {
            pages.add(MirroredPage.builder()
                    .id("id-" + i)
                    .url(URI.create(urlLocalPathPairs[i]))
                    .canonicalUrl(URI.create(urlLocalPathPairs[i]))
                    .localHtmlPath(urlLocalPathPairs[i + 1])
                    .depth(0).discoveredOrder(i / 2L).status(200).fetchedAt(FETCHED_AT)
                    .mirrorStatus(MirrorStatus.SUCCESS)
                    .build());
        }
        return PageLinkRewritePlan.from(MirrorManifest.builder()
                .startUrl(BASE).sameDomainOnly(true).maxPages(10).maxDepth(2).pages(pages).build());
    }

    private static AssetLinkRewritePlan planWithAssets(final String... urlLocalPathPairs) {
        final java.util.List<AssetMetadata> assets = new java.util.ArrayList<>();
        for (int i = 0; i < urlLocalPathPairs.length; i += 2) {
            final String url = urlLocalPathPairs[i];
            final String localPath = urlLocalPathPairs[i + 1];
            assets.add(AssetMetadata.builder()
                    .id("asset-" + i)
                    .url(URI.create(url))
                    .assetType(AssetType.IMAGE)
                    .localAssetPath(localPath)
                    .statusCode(200)
                    .assetStatus(AssetStatus.SUCCESS)
                    .build());
        }
        return AssetLinkRewritePlan.from(AssetManifest.builder().assets(assets).build());
    }
}
