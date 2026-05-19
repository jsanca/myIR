package codex.ir.ingestion.crawler.internal.metadata;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonLdBlockExtractorTest {

    private final JsonLdBlockExtractor extractor = new JsonLdBlockExtractor();

    @Test
    void shouldExtractSingleJsonLdBlock() {
        final Document doc = parse("""
                <html><head>
                <script type="application/ld+json">{"@type": "WebPage"}</script>
                </head></html>""");

        final List<String> blocks = extractor.extract(doc);

        assertEquals(1, blocks.size());
        assertTrue(blocks.get(0).contains("WebPage"));
    }

    @Test
    void shouldExtractMultipleJsonLdBlocks() {
        final Document doc = parse("""
                <html><head>
                <script type="application/ld+json">{"@type": "WebSite"}</script>
                <script type="application/ld+json">{"@type": "Product"}</script>
                </head></html>""");

        assertEquals(2, extractor.extract(doc).size());
    }

    @Test
    void shouldReturnEmptyListWhenNoJsonLdScripts() {
        final Document doc = parse("<html><head></head></html>");

        assertTrue(extractor.extract(doc).isEmpty());
    }

    @Test
    void shouldSkipEmptyJsonLdScripts() {
        final Document doc = parse("""
                <html><head>
                <script type="application/ld+json">  </script>
                <script type="application/ld+json">{"@type": "WebPage"}</script>
                </head></html>""");

        assertEquals(1, extractor.extract(doc).size());
    }

    @Test
    void shouldNotExtractOtherScriptTypes() {
        final Document doc = parse("""
                <html><head>
                <script type="text/javascript">var x = 1;</script>
                <script type="application/ld+json">{"@type": "WebPage"}</script>
                </head></html>""");

        assertEquals(1, extractor.extract(doc).size());
    }

    private static Document parse(final String html) {
        return Jsoup.parse(html, "https://example.com/");
    }
}
