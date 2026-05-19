package codex.ir.ingestion.crawler.internal.metadata;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TwitterCardExtractorTest {

    private final TwitterCardExtractor extractor = new TwitterCardExtractor();

    @Test
    void shouldExtractTwitterCardFields() {
        final Document doc = parse("""
                <html><head>
                <meta name="twitter:card" content="summary_large_image">
                <meta name="twitter:title" content="Tweet Title">
                <meta name="twitter:description" content="Tweet Description">
                </head></html>""");

        final Map<String, String> tc = extractor.extract(doc);

        assertEquals("summary_large_image", tc.get("twitter:card"));
        assertEquals("Tweet Title", tc.get("twitter:title"));
        assertEquals("Tweet Description", tc.get("twitter:description"));
    }

    @Test
    void shouldReturnEmptyMapWhenNoTwitterTags() {
        final Document doc = parse("<html><head></head></html>");

        assertTrue(extractor.extract(doc).isEmpty());
    }

    @Test
    void shouldNotIncludeNonTwitterMetaTags() {
        final Document doc = parse("""
                <html><head>
                <meta name="description" content="Not twitter">
                <meta name="twitter:site" content="@example">
                </head></html>""");

        final Map<String, String> tc = extractor.extract(doc);

        assertEquals(1, tc.size());
        assertEquals("@example", tc.get("twitter:site"));
    }

    private static Document parse(final String html) {
        return Jsoup.parse(html, "https://example.com/");
    }
}
