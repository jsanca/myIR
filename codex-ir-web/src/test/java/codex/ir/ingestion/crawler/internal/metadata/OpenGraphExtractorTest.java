package codex.ir.ingestion.crawler.internal.metadata;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenGraphExtractorTest {

    private final OpenGraphExtractor extractor = new OpenGraphExtractor();

    @Test
    void shouldExtractOgTitleAndDescription() {
        final Document doc = parse("""
                <html><head>
                <meta property="og:title" content="OG Title">
                <meta property="og:description" content="OG Description">
                </head></html>""");

        final Map<String, String> og = extractor.extract(doc);

        assertEquals("OG Title", og.get("og:title"));
        assertEquals("OG Description", og.get("og:description"));
    }

    @Test
    void shouldExtractOgImageAndUrl() {
        final Document doc = parse("""
                <html><head>
                <meta property="og:image" content="https://example.com/img.jpg">
                <meta property="og:url" content="https://example.com/page/">
                </head></html>""");

        final Map<String, String> og = extractor.extract(doc);

        assertEquals("https://example.com/img.jpg", og.get("og:image"));
        assertEquals("https://example.com/page/", og.get("og:url"));
    }

    @Test
    void shouldReturnEmptyMapWhenNoOgTags() {
        final Document doc = parse("<html><head></head></html>");

        assertTrue(extractor.extract(doc).isEmpty());
    }

    @Test
    void shouldSkipOgTagsWithBlankContent() {
        final Document doc = parse("""
                <html><head>
                <meta property="og:title" content="  ">
                <meta property="og:type" content="website">
                </head></html>""");

        final Map<String, String> og = extractor.extract(doc);

        assertEquals(1, og.size());
        assertEquals("website", og.get("og:type"));
    }

    private static Document parse(final String html) {
        return Jsoup.parse(html, "https://example.com/");
    }
}
