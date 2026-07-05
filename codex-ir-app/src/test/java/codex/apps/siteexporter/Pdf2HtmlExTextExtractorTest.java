package codex.apps.siteexporter;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class Pdf2HtmlExTextExtractorTest {

    private static final String BASE_URI = "https://example.com/";
    private final Pdf2HtmlExTextExtractor extractor = new Pdf2HtmlExTextExtractor();

    // ------------------------------------------------------------------
    // Null guards
    // ------------------------------------------------------------------

    @Test
    void extractDocumentShouldThrowWhenDocumentIsNull() {
        assertThrows(NullPointerException.class, () -> extractor.extract((Document) null));
    }

    @Test
    void extractStringShouldThrowWhenHtmlIsNull() {
        assertThrows(NullPointerException.class, () -> extractor.extract(null, BASE_URI));
    }

    @Test
    void extractStringShouldThrowWhenBaseUriIsNull() {
        assertThrows(NullPointerException.class,
                () -> extractor.extract("<html></html>", null));
    }

    // ------------------------------------------------------------------
    // Empty / edge cases
    // ------------------------------------------------------------------

    @Test
    void extractShouldReturnEmptyDocumentWhenNoPfDivsPresent() {
        final Document doc = Jsoup.parse(
                "<html><head><title>Test</title></head><body><p>Plain page</p></body></html>",
                BASE_URI);
        final ReaderDocument result = extractor.extract(doc);
        assertTrue(result.pages().isEmpty(), "no .pf divs must produce an empty page list");
    }

    @Test
    void extractShouldUseDocumentTitleAsPresentInHead() {
        final Document doc = Jsoup.parse(
                "<html><head><title>My Book</title></head>"
                + "<body><div class=\"pf\"><span class=\"t\">Hello</span></div></body></html>",
                BASE_URI);
        final ReaderDocument result = extractor.extract(doc);
        assertEquals("My Book", result.title());
    }

    @Test
    void extractShouldFallBackToLiteralDocumentWhenTitleIsBlank() {
        final Document doc = Jsoup.parse(
                "<html><body><div class=\"pf\"><span class=\"t\">Hi</span></div></body></html>",
                BASE_URI);
        final ReaderDocument result = extractor.extract(doc);
        assertEquals("Document", result.title());
    }

    @Test
    void extractShouldProduceOneReaderPagePerPfDiv() {
        final String html =
                "<html><body>"
                + "<div class=\"pf\"><span class=\"t\">Page one</span></div>"
                + "<div class=\"pf\"><span class=\"t\">Page two</span></div>"
                + "</body></html>";
        final ReaderDocument result = extractor.extract(html, BASE_URI);
        assertEquals(2, result.pages().size(), "one ReaderPage per .pf element");
    }

    @Test
    void extractShouldUseDataPageNoAttributeWhenPresent() {
        final String html =
                "<html><body>"
                + "<div class=\"pf\" data-page-no=\"5\"><span class=\"t\">text</span></div>"
                + "</body></html>";
        final ReaderDocument result = extractor.extract(html, BASE_URI);
        assertEquals(5, result.pages().get(0).pageNumber());
    }

    @Test
    void extractShouldFallBackToSequentialPageNumberWhenAttributeAbsent() {
        final String html =
                "<html><body>"
                + "<div class=\"pf\"><span class=\"t\">A</span></div>"
                + "<div class=\"pf\"><span class=\"t\">B</span></div>"
                + "</body></html>";
        final ReaderDocument result = extractor.extract(html, BASE_URI);
        assertEquals(1, result.pages().get(0).pageNumber());
        assertEquals(2, result.pages().get(1).pageNumber());
    }

    @Test
    void extractShouldPreserveDocumentOrderWhenNoCssCoordinatesArePresent() {
        // No coordinate CSS classes → document order preserved
        final String html =
                "<html><body><div class=\"pf\">"
                + "<span class=\"t\">Alpha</span>"
                + "<span class=\"t\">Beta</span>"
                + "<span class=\"t\">Gamma</span>"
                + "</div></body></html>";
        final ReaderDocument result = extractor.extract(html, BASE_URI);
        final String allText = String.join(" ", result.pages().get(0).paragraphs());
        final int alphaIdx  = allText.indexOf("Alpha");
        final int betaIdx   = allText.indexOf("Beta");
        final int gammaIdx  = allText.indexOf("Gamma");
        assertTrue(alphaIdx < betaIdx && betaIdx < gammaIdx,
                "document order must be preserved when no CSS coordinates are present");
    }

    @Test
    void extractShouldSkipSpansWithEmptyText() {
        final String html =
                "<html><body><div class=\"pf\">"
                + "<span class=\"t\">   </span>"
                + "<span class=\"t\">Real content</span>"
                + "</div></body></html>";
        final ReaderDocument result = extractor.extract(html, BASE_URI);
        final String allText = String.join(" ", result.pages().get(0).paragraphs());
        assertTrue(allText.contains("Real content"));
        assertFalse(allText.isBlank());
    }

    // ------------------------------------------------------------------
    // Fixture-based validation tests
    // ------------------------------------------------------------------

    @Test
    void extractFromFixtureShouldProduceExactlyOnePage() throws Exception {
        final ReaderDocument doc = extractFromFixture();
        assertEquals(1, doc.pages().size(),
                "the deeplearning_part1 fixture has one .pf page");
    }

    @Test
    void extractFromFixtureShouldHavePageNumber1() throws Exception {
        final ReaderDocument doc = extractFromFixture();
        assertEquals(1, doc.pages().get(0).pageNumber());
    }

    @Test
    void extractFromFixtureShouldContainPartI() throws Exception {
        final ReaderDocument doc = extractFromFixture();
        final String allText = collectAllText(doc);
        assertTrue(allText.contains("Part I"),
                "extracted text must contain 'Part I'");
    }

    @Test
    void extractFromFixtureShouldContainSubtitle() throws Exception {
        final ReaderDocument doc = extractFromFixture();
        final String allText = collectAllText(doc);
        assertTrue(allText.contains("Applied Math and Machine Learning Basics"),
                "extracted text must contain 'Applied Math and Machine Learning Basics'");
    }

    @Test
    void extractFromFixtureShouldContainBodyTextPrefix() throws Exception {
        final ReaderDocument doc = extractFromFixture();
        final String allText = collectAllText(doc);
        assertTrue(allText.contains("This part of the book introduces"),
                "extracted text must contain 'This part of the book introduces...'");
    }

    @Test
    void extractFromFixtureShouldExtractAtLeastThreeParagraphs() throws Exception {
        final ReaderDocument doc = extractFromFixture();
        final int total = doc.totalParagraphs();
        assertTrue(total >= 3,
                "fixture has three logical text blocks (heading, subtitle, body paragraph); got " + total);
    }

    @Test
    void extractFromFixtureShouldSortPartIBeforeSubtitle() throws Exception {
        final ReaderDocument doc = extractFromFixture();
        final List<String> paras = doc.pages().get(0).paragraphs();
        // Part I (y3=700) is above the subtitle (y2=660) in bottom-based coordinates
        int partIIdx = -1, subtitleIdx = -1;
        for (int i = 0; i < paras.size(); i++) {
            if (paras.get(i).contains("Part I"))   partIIdx   = i;
            if (paras.get(i).contains("Applied Math")) subtitleIdx = i;
        }
        assertTrue(partIIdx >= 0,   "must find 'Part I' paragraph");
        assertTrue(subtitleIdx >= 0, "must find subtitle paragraph");
        assertTrue(partIIdx < subtitleIdx,
                "'Part I' must appear before the subtitle (top-to-bottom sort)");
    }

    @Test
    void extractFromFixtureShouldJoinBodyLinesToSingleParagraph() throws Exception {
        final ReaderDocument doc = extractFromFixture();
        final String allText = collectAllText(doc);
        // The four body lines (y1=500, y4=486, y5=472, y6=458) have gaps of 14px < 1.5*12=18
        // → they should be joined into one paragraph
        assertTrue(allText.contains("This part of the book introduces"),
                "body lines must be present");
        assertTrue(allText.contains("the mathematical and statistical foundations"),
                "continuation body text must be present");
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private ReaderDocument extractFromFixture() throws Exception {
        final String html = Pdf2HtmlExDetectorTest.loadFixture();
        return extractor.extract(html, BASE_URI);
    }

    private static String collectAllText(final ReaderDocument doc) {
        return doc.pages().stream()
                .flatMap(p -> p.paragraphs().stream())
                .collect(java.util.stream.Collectors.joining(" "));
    }
}
