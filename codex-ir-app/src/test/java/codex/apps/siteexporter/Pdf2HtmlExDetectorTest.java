package codex.apps.siteexporter;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Pdf2HtmlExDetectorTest {

    private static final String BASE_URI = "https://example.com/";
    private final Pdf2HtmlExDetector detector = new Pdf2HtmlExDetector();

    // ------------------------------------------------------------------
    // Null guards
    // ------------------------------------------------------------------

    @Test
    void detectDocumentShouldThrowWhenDocumentIsNull() {
        assertThrows(NullPointerException.class, () -> detector.detect((Document) null));
    }

    @Test
    void detectStringShouldThrowWhenHtmlIsNull() {
        assertThrows(NullPointerException.class, () -> detector.detect(null, BASE_URI));
    }

    @Test
    void detectStringShouldThrowWhenBaseUriIsNull() {
        assertThrows(NullPointerException.class,
                () -> detector.detect("<html></html>", null));
    }

    // ------------------------------------------------------------------
    // Positive signals
    // ------------------------------------------------------------------

    @Test
    void detectShouldReturnTrueWhenMetaGeneratorContainsPdf2HtmlEx() {
        final Document doc = Jsoup.parse(
                "<html><head><meta name=\"generator\" content=\"pdf2htmlEX\"/></head><body></body></html>",
                BASE_URI);
        assertTrue(detector.detect(doc), "meta generator tag must trigger detection");
    }

    @Test
    void detectShouldReturnTrueWhenMetaGeneratorIsCaseInsensitive() {
        final Document doc = Jsoup.parse(
                "<html><head><meta name=\"generator\" content=\"PDF2HTMLEX\"/></head><body></body></html>",
                BASE_URI);
        assertTrue(detector.detect(doc), "detection must be case-insensitive for meta content");
    }

    @Test
    void detectShouldReturnTrueWhenCreatedByCommentInBody() {
        final Document doc = Jsoup.parse(
                "<html><body><!-- Created by pdf2htmlEX --><p>Text</p></body></html>",
                BASE_URI);
        assertTrue(detector.detect(doc), "Created-by comment in body must trigger detection");
    }

    @Test
    void detectShouldReturnTrueWhenStructuralFingerprintPresent() {
        final Document doc = Jsoup.parse(
                "<html><body>"
                + "<div id=\"page-container\">"
                + "<div class=\"pf\"><span class=\"t\">text</span></div>"
                + "</div>"
                + "</body></html>",
                BASE_URI);
        assertTrue(detector.detect(doc), "page-container + .pf + .t structure must trigger detection");
    }

    @Test
    void detectShouldReturnTrueForFixtureFile() throws Exception {
        final String html = loadFixture();
        assertTrue(detector.detect(html, BASE_URI),
                "detection must return true for the deeplearning_part1 fixture");
    }

    // ------------------------------------------------------------------
    // Negative signals
    // ------------------------------------------------------------------

    @Test
    void detectShouldReturnFalseForRegularHtmlPage() {
        final Document doc = Jsoup.parse(
                "<html><head><title>Blog post</title></head>"
                + "<body><h1>Hello</h1><p>World</p></body></html>",
                BASE_URI);
        assertFalse(detector.detect(doc), "ordinary HTML must not be classified as pdf2htmlEX");
    }

    @Test
    void detectShouldReturnFalseWhenOnlyPageContainerIsPresentWithoutPfAndT() {
        final Document doc = Jsoup.parse(
                "<html><body><div id=\"page-container\"><div>text</div></div></body></html>",
                BASE_URI);
        assertFalse(detector.detect(doc),
                "partial structural fingerprint (missing .pf and .t) must not trigger detection");
    }

    @Test
    void detectShouldReturnFalseForEmptyDocument() {
        final Document doc = Jsoup.parse("", BASE_URI);
        assertFalse(detector.detect(doc), "empty document must not be classified as pdf2htmlEX");
    }

    // ------------------------------------------------------------------
    // Fixture helper
    // ------------------------------------------------------------------

    static String loadFixture() throws Exception {
        try (final var stream = Pdf2HtmlExDetectorTest.class
                .getResourceAsStream("/fixtures/pdf2htmlex/deeplearning_part1.html")) {
            assertNotNull(stream, "fixture file must exist on the classpath");
            return new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }
}
