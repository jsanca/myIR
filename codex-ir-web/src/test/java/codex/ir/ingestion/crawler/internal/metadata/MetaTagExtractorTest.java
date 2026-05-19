package codex.ir.ingestion.crawler.internal.metadata;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetaTagExtractorTest {

    private final MetaTagExtractor extractor = new MetaTagExtractor();

    @Test
    void shouldExtractTitleFromTitleTag() {
        final Document doc = parse("<html><head><title>My Page</title></head></html>");

        final MetaTagExtractor.StandardMetaTags tags = extractor.extract(doc);

        assertTrue(tags.title().isPresent());
        assertEquals("My Page", tags.title().get());
    }

    @Test
    void shouldReturnEmptyTitleWhenTagAbsent() {
        final Document doc = parse("<html><head></head></html>");

        assertTrue(extractor.extract(doc).title().isEmpty());
    }

    @Test
    void shouldExtractDescriptionFromMetaTag() {
        final Document doc = parse("""
                <html><head>
                <meta name="description" content="A useful description">
                </head></html>""");

        final MetaTagExtractor.StandardMetaTags tags = extractor.extract(doc);

        assertTrue(tags.description().isPresent());
        assertEquals("A useful description", tags.description().get());
    }

    @Test
    void shouldReturnEmptyDescriptionWhenMetaAbsent() {
        final Document doc = parse("<html><head></head></html>");

        assertTrue(extractor.extract(doc).description().isEmpty());
    }

    @Test
    void shouldExtractCanonicalUrl() {
        final Document doc = parse("""
                <html><head>
                <link rel="canonical" href="https://example.com/page/">
                </head></html>""");

        final MetaTagExtractor.StandardMetaTags tags = extractor.extract(doc);

        assertTrue(tags.canonicalUrl().isPresent());
        assertEquals("https://example.com/page/", tags.canonicalUrl().get());
    }

    @Test
    void shouldReturnEmptyCanonicalWhenLinkAbsent() {
        final Document doc = parse("<html><head></head></html>");

        assertTrue(extractor.extract(doc).canonicalUrl().isEmpty());
    }

    @Test
    void shouldReturnEmptyDescriptionWhenContentIsBlank() {
        final Document doc = parse("""
                <html><head>
                <meta name="description" content="   ">
                </head></html>""");

        assertTrue(extractor.extract(doc).description().isEmpty());
    }

    @Test
    void shouldExtractAllThreeFieldsInOneCall() {
        final Document doc = parse("""
                <html><head>
                <title>Full Page</title>
                <meta name="description" content="Page desc">
                <link rel="canonical" href="https://example.com/full/">
                </head></html>""");

        final MetaTagExtractor.StandardMetaTags tags = extractor.extract(doc);

        assertEquals("Full Page", tags.title().orElse(null));
        assertEquals("Page desc", tags.description().orElse(null));
        assertEquals("https://example.com/full/", tags.canonicalUrl().orElse(null));
    }

    private static Document parse(final String html) {
        return Jsoup.parse(html, "https://example.com/");
    }
}
