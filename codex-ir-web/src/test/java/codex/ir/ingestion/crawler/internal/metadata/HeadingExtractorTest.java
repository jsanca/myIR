package codex.ir.ingestion.crawler.internal.metadata;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeadingExtractorTest {

    private final HeadingExtractor extractor = new HeadingExtractor();

    @Test
    void shouldExtractSingleH1() {
        final Document doc = parse("<html><body><h1>Main Title</h1></body></html>");

        assertEquals(List.of("Main Title"), extractor.extract(doc).h1());
    }

    @Test
    void shouldExtractMultipleH1s() {
        final Document doc = parse("""
                <html><body>
                <h1>First</h1>
                <h1>Second</h1>
                </body></html>""");

        assertEquals(List.of("First", "Second"), extractor.extract(doc).h1());
    }

    @Test
    void shouldExtractH2Headings() {
        final Document doc = parse("""
                <html><body>
                <h2>Section One</h2>
                <h2>Section Two</h2>
                </body></html>""");

        assertEquals(List.of("Section One", "Section Two"), extractor.extract(doc).h2());
    }

    @Test
    void shouldReturnEmptyListWhenNoH1() {
        final Document doc = parse("<html><body><h2>Only h2</h2></body></html>");

        assertTrue(extractor.extract(doc).h1().isEmpty());
    }

    @Test
    void shouldReturnEmptyListWhenNoH2() {
        final Document doc = parse("<html><body><h1>Only h1</h1></body></html>");

        assertTrue(extractor.extract(doc).h2().isEmpty());
    }

    @Test
    void shouldSkipBlankHeadings() {
        final Document doc = parse("""
                <html><body>
                <h1>   </h1>
                <h1>Real Heading</h1>
                </body></html>""");

        assertEquals(List.of("Real Heading"), extractor.extract(doc).h1());
    }

    @Test
    void shouldExtractBothH1AndH2InOneCall() {
        final Document doc = parse("""
                <html><body>
                <h1>Top</h1>
                <h2>Sub A</h2>
                <h2>Sub B</h2>
                </body></html>""");

        final HeadingExtractor.Headings headings = extractor.extract(doc);

        assertEquals(List.of("Top"), headings.h1());
        assertEquals(List.of("Sub A", "Sub B"), headings.h2());
    }

    private static Document parse(final String html) {
        return Jsoup.parse(html, "https://example.com/");
    }
}
