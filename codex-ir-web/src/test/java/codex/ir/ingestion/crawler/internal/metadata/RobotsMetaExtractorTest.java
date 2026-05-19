package codex.ir.ingestion.crawler.internal.metadata;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RobotsMetaExtractorTest {

    private final RobotsMetaExtractor extractor = new RobotsMetaExtractor();

    @Test
    void shouldExtractSingleDirective() {
        final Document doc = parse("""
                <html><head>
                <meta name="robots" content="noindex">
                </head></html>""");

        final List<String> directives = extractor.extract(doc);

        assertEquals(List.of("noindex"), directives);
    }

    @Test
    void shouldExtractMultipleDirectives() {
        final Document doc = parse("""
                <html><head>
                <meta name="robots" content="noindex, nofollow">
                </head></html>""");

        final List<String> directives = extractor.extract(doc);

        assertEquals(List.of("noindex", "nofollow"), directives);
    }

    @Test
    void shouldReturnEmptyListWhenRobotsMetaAbsent() {
        final Document doc = parse("<html><head></head></html>");

        assertTrue(extractor.extract(doc).isEmpty());
    }

    @Test
    void shouldTrimWhitespaceFromDirectives() {
        final Document doc = parse("""
                <html><head>
                <meta name="robots" content="  noindex ,  nofollow  ">
                </head></html>""");

        final List<String> directives = extractor.extract(doc);

        assertEquals(List.of("noindex", "nofollow"), directives);
    }

    private static Document parse(final String html) {
        return Jsoup.parse(html, "https://example.com/");
    }
}
