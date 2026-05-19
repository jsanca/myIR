package codex.ir.ingestion.crawler.metadata;

import codex.ir.ingestion.WebPage;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PageMetadataExtractorsTest {

    private static final Instant NOW = Instant.now();
    private final PageMetadataExtractor extractor = PageMetadataExtractors.jsoupDefault();

    @Test
    void shouldExtractTitleAndDescription() {
        final WebPage page = page("https://example.com/", """
                <html><head>
                <title>Example Site</title>
                <meta name="description" content="A great site">
                </head></html>""");

        final PageMetadata meta = extractor.extract(page);

        assertEquals("Example Site", meta.title().orElse(null));
        assertEquals("A great site", meta.description().orElse(null));
    }

    @Test
    void shouldExtractCanonicalUrl() {
        final WebPage page = page("https://example.com/", """
                <html><head>
                <link rel="canonical" href="https://example.com/canonical/">
                </head></html>""");

        final PageMetadata meta = extractor.extract(page);

        assertTrue(meta.canonicalUrl().isPresent());
        assertEquals("https://example.com/canonical/", meta.canonicalUrl().get());
    }

    @Test
    void shouldExtractOpenGraphMetadata() {
        final WebPage page = page("https://example.com/", """
                <html><head>
                <meta property="og:title" content="OG Title">
                <meta property="og:type" content="article">
                <meta property="og:image" content="https://example.com/img.jpg">
                </head></html>""");

        final PageMetadata meta = extractor.extract(page);

        assertEquals("OG Title", meta.openGraph().get("og:title"));
        assertEquals("article", meta.openGraph().get("og:type"));
        assertEquals("https://example.com/img.jpg", meta.openGraph().get("og:image"));
    }

    @Test
    void shouldExtractTwitterCardMetadata() {
        final WebPage page = page("https://example.com/", """
                <html><head>
                <meta name="twitter:card" content="summary">
                <meta name="twitter:creator" content="@author">
                </head></html>""");

        final PageMetadata meta = extractor.extract(page);

        assertEquals("summary", meta.twitterCard().get("twitter:card"));
        assertEquals("@author", meta.twitterCard().get("twitter:creator"));
    }

    @Test
    void shouldExtractRobotsDirectives() {
        final WebPage page = page("https://example.com/", """
                <html><head>
                <meta name="robots" content="noindex, nofollow">
                </head></html>""");

        final PageMetadata meta = extractor.extract(page);

        assertEquals(2, meta.robotsDirectives().size());
        assertTrue(meta.robotsDirectives().contains("noindex"));
        assertTrue(meta.robotsDirectives().contains("nofollow"));
    }

    @Test
    void shouldExtractH1AndH2Headings() {
        final WebPage page = page("https://example.com/", """
                <html><body>
                <h1>Main Heading</h1>
                <h2>Sub One</h2>
                <h2>Sub Two</h2>
                </body></html>""");

        final PageMetadata meta = extractor.extract(page);

        assertEquals(1, meta.h1Headings().size());
        assertEquals("Main Heading", meta.h1Headings().get(0));
        assertEquals(2, meta.h2Headings().size());
    }

    @Test
    void shouldExtractPageLanguage() {
        final WebPage page = page("https://example.com/", """
                <html lang="es"><head><title>Hola</title></head></html>""");

        final PageMetadata meta = extractor.extract(page);

        assertTrue(meta.language().isPresent());
        assertEquals("es", meta.language().get());
    }

    @Test
    void shouldExtractJsonLdBlocks() {
        final WebPage page = page("https://example.com/", """
                <html><head>
                <script type="application/ld+json">{"@type": "WebPage", "name": "Test"}</script>
                </head></html>""");

        final PageMetadata meta = extractor.extract(page);

        assertEquals(1, meta.jsonLdBlocks().size());
        assertTrue(meta.jsonLdBlocks().get(0).contains("WebPage"));
    }

    @Test
    void shouldReturnEmptyMetadataForNullHtml() {
        final WebPage page = new WebPage(
                URI.create("https://example.com/"),
                null, "", "", 200, "text/html", NOW, Map.of());

        final PageMetadata meta = extractor.extract(page);

        assertTrue(meta.title().isEmpty());
        assertTrue(meta.openGraph().isEmpty());
        assertTrue(meta.jsonLdBlocks().isEmpty());
    }

    @Test
    void shouldReturnEmptyMetadataForBlankHtml() {
        final WebPage page = new WebPage(
                URI.create("https://example.com/"),
                "   ", "", "", 200, "text/html", NOW, Map.of());

        final PageMetadata meta = extractor.extract(page);

        assertTrue(meta.title().isEmpty());
        assertTrue(meta.h1Headings().isEmpty());
    }

    @Test
    void shouldThrowNpeForNullPage() {
        assertThrows(NullPointerException.class, () -> extractor.extract(null));
    }

    @Test
    void shouldReturnEmptyLanguageWhenHtmlLangAbsent() {
        final WebPage page = page("https://example.com/", """
                <html><head><title>No lang attribute</title></head></html>""");

        assertTrue(extractor.extract(page).language().isEmpty());
    }

    @Test
    void shouldHandleFullPageWithAllFields() {
        final WebPage page = page("https://example.com/article/", """
                <html lang="en"><head>
                <title>Full Article</title>
                <meta name="description" content="Article description">
                <link rel="canonical" href="https://example.com/article/">
                <meta property="og:title" content="Full Article OG">
                <meta property="og:type" content="article">
                <meta name="twitter:card" content="summary_large_image">
                <meta name="robots" content="index, follow">
                <script type="application/ld+json">{"@type": "Article", "name": "Full Article"}</script>
                </head><body>
                <h1>Full Article</h1>
                <h2>Introduction</h2>
                <h2>Conclusion</h2>
                </body></html>""");

        final PageMetadata meta = extractor.extract(page);

        assertEquals("Full Article", meta.title().orElse(null));
        assertEquals("Article description", meta.description().orElse(null));
        assertTrue(meta.canonicalUrl().isPresent());
        assertEquals(2, meta.openGraph().size());
        assertEquals(1, meta.twitterCard().size());
        assertEquals(2, meta.robotsDirectives().size());
        assertEquals("en", meta.language().orElse(null));
        assertEquals(1, meta.jsonLdBlocks().size());
        assertEquals(1, meta.h1Headings().size());
        assertEquals(2, meta.h2Headings().size());
    }

    private static WebPage page(final String url, final String html) {
        return new WebPage(URI.create(url), html, "", "", 200, "text/html", NOW, Map.of());
    }
}
