package codex.apps.siteexporter;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AssetReferenceExtractorTest {

    private static final URI PAGE_URL = URI.create("https://example.com/page");
    private final AssetReferenceExtractor extractor = new AssetReferenceExtractor();

    @Test
    void shouldExtractImageFromImgSrc() {
        final String html = "<html><body><img src=\"/images/logo.png\"></body></html>";

        final List<AssetReference> refs = extractor.extract(html, PAGE_URL);

        assertEquals(1, refs.size());
        assertEquals(URI.create("https://example.com/images/logo.png"), refs.get(0).url());
        assertEquals(AssetType.IMAGE, refs.get(0).type());
    }

    @Test
    void shouldExtractStylesheetFromLinkHref() {
        final String html = "<html><head><link rel=\"stylesheet\" href=\"/css/style.css\"></head></html>";

        final List<AssetReference> refs = extractor.extract(html, PAGE_URL);

        assertEquals(1, refs.size());
        assertEquals(URI.create("https://example.com/css/style.css"), refs.get(0).url());
        assertEquals(AssetType.STYLESHEET, refs.get(0).type());
    }

    @Test
    void shouldExtractScriptFromScriptSrc() {
        final String html = "<html><head><script src=\"/js/app.js\"></script></head></html>";

        final List<AssetReference> refs = extractor.extract(html, PAGE_URL);

        assertEquals(1, refs.size());
        assertEquals(URI.create("https://example.com/js/app.js"), refs.get(0).url());
        assertEquals(AssetType.SCRIPT, refs.get(0).type());
    }

    @Test
    void shouldExtractMultipleAssetTypes() {
        final String html = """
                <html>
                <head>
                  <link rel="stylesheet" href="/css/main.css">
                  <script src="/js/app.js"></script>
                </head>
                <body>
                  <img src="/img/hero.png">
                  <img src="/img/thumb.jpg">
                </body>
                </html>
                """;

        final List<AssetReference> refs = extractor.extract(html, PAGE_URL);

        assertEquals(4, refs.size());
    }

    @Test
    void shouldResolveRelativeUrlsUsingPageUrl() {
        final URI pageUrl = URI.create("https://example.com/section/page.html");
        final String html = "<html><body><img src=\"../img/logo.png\"></body></html>";

        final List<AssetReference> refs = extractor.extract(html, pageUrl);

        assertEquals(1, refs.size());
        assertEquals(URI.create("https://example.com/img/logo.png"), refs.get(0).url());
    }

    @Test
    void shouldResolveAbsoluteUrlsUnchanged() {
        final String html = "<html><body><img src=\"https://cdn.example.com/logo.png\"></body></html>";

        final List<AssetReference> refs = extractor.extract(html, PAGE_URL);

        assertEquals(1, refs.size());
        assertEquals(URI.create("https://cdn.example.com/logo.png"), refs.get(0).url());
    }

    @Test
    void shouldDeduplicateIdenticalUrls() {
        final String html = """
                <html><body>
                  <img src="/img/logo.png">
                  <img src="/img/logo.png">
                </body></html>
                """;

        final List<AssetReference> refs = extractor.extract(html, PAGE_URL);

        assertEquals(1, refs.size());
    }

    @Test
    void shouldFilterOutNonHttpUrls() {
        final String html = """
                <html><body>
                  <img src="data:image/png;base64,abc">
                  <img src="ftp://files.example.com/img.png">
                  <img src="/img/valid.png">
                </body></html>
                """;

        final List<AssetReference> refs = extractor.extract(html, PAGE_URL);

        assertEquals(1, refs.size());
        assertEquals(URI.create("https://example.com/img/valid.png"), refs.get(0).url());
    }

    @Test
    void shouldIgnoreImgWithoutSrc() {
        final String html = "<html><body><img alt=\"no src\"></body></html>";

        final List<AssetReference> refs = extractor.extract(html, PAGE_URL);

        assertTrue(refs.isEmpty());
    }

    @Test
    void shouldIgnoreLinkWithoutStylesheetRel() {
        final String html = "<html><head><link rel=\"icon\" href=\"/favicon.ico\"></head></html>";

        final List<AssetReference> refs = extractor.extract(html, PAGE_URL);

        assertTrue(refs.isEmpty());
    }

    @Test
    void shouldReturnEmptyListForHtmlWithNoAssets() {
        final String html = "<html><body><p>Hello</p></body></html>";

        final List<AssetReference> refs = extractor.extract(html, PAGE_URL);

        assertTrue(refs.isEmpty());
    }

    @Test
    void shouldPreserveInsertionOrderAcrossTypes() {
        final String html = """
                <html>
                <head>
                  <link rel="stylesheet" href="/css/a.css">
                  <script src="/js/b.js"></script>
                </head>
                <body>
                  <img src="/img/c.png">
                </body>
                </html>
                """;

        final List<AssetReference> refs = extractor.extract(html, PAGE_URL);

        assertEquals(3, refs.size());
        assertEquals(AssetType.STYLESHEET, refs.get(0).type());
        assertEquals(AssetType.SCRIPT, refs.get(1).type());
        assertEquals(AssetType.IMAGE, refs.get(2).type());
    }
}
