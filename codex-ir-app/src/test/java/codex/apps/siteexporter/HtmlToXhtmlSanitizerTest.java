package codex.apps.siteexporter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HtmlToXhtmlSanitizerTest {

    private static final String BASE_URI = "https://example.com/";
    private final HtmlToXhtmlSanitizer sanitizer = new HtmlToXhtmlSanitizer();

    // ------------------------------------------------------------------
    // sanitize() — basic XHTML conversion
    // ------------------------------------------------------------------

    @Test
    void sanitizeShouldThrowWhenHtmlIsNull() {
        assertThrows(NullPointerException.class, () -> sanitizer.sanitize(null, BASE_URI));
    }

    @Test
    void sanitizeShouldThrowWhenBaseUriIsNull() {
        assertThrows(NullPointerException.class,
                () -> sanitizer.sanitize("<html></html>", null));
    }

    @Test
    void sanitizeShouldSelfCloseVoidBrElement() {
        final String result = sanitizer.sanitize("<html><body><p>line1<br>line2</p></body></html>", BASE_URI);
        // In XML/XHTML mode Jsoup outputs <br /> (self-closed)
        assertFalse(result.contains("<br>"), "bare <br> must be converted to self-closing form");
        assertTrue(result.contains("<br"), "br element must still be present");
    }

    @Test
    void sanitizeShouldSelfCloseMetaCharsetElement() {
        final String result = sanitizer.sanitize(
                "<html><head><meta charset=\"utf-8\"></head><body></body></html>", BASE_URI);
        assertFalse(result.contains("<meta charset=\"utf-8\">"),
                "bare <meta> must be converted to self-closing form");
        assertTrue(result.contains("charset"), "charset attribute must be preserved");
    }

    @Test
    void sanitizeShouldSelfCloseImgElement() {
        final String result = sanitizer.sanitize(
                "<html><body><img src=\"logo.png\" alt=\"logo\"></body></html>", BASE_URI);
        // In XML mode the bare <img ...> becomes <img ... /> — the bare form must not appear
        assertFalse(result.contains("alt=\"logo\">"),
                "bare <img> must be converted to self-closing form");
        assertTrue(result.contains("logo.png"), "img src attribute must be preserved");
    }

    @Test
    void sanitizeShouldCloseUnclosedParagraphTags() {
        final String result = sanitizer.sanitize(
                "<html><body><p>First<p>Second</body></html>", BASE_URI);
        // Jsoup closes implicit p tags: each <p> must be followed by </p>
        final int pOpen  = countOccurrences(result, "<p>");
        final int pClose = countOccurrences(result, "</p>");
        assertEquals(pOpen, pClose, "every <p> must have a matching </p>");
    }

    @Test
    void sanitizeShouldProduceWellFormedOutputForEmptyBody() {
        final String result = sanitizer.sanitize("<html><body></body></html>", BASE_URI);
        assertNotNull(result);
        assertTrue(result.contains("<body>") || result.contains("<body/>"),
                "body element must appear in output");
    }

    @Test
    void sanitizeShouldHandleLeadingWhitespaceBeforeDoctype() {
        final String html = "   \n<!DOCTYPE html>\n<html><body><p>Hello</p></body></html>";
        final String result = sanitizer.sanitize(html, BASE_URI);
        assertNotNull(result);
        assertTrue(result.contains("Hello"), "body content must be preserved");
    }

    @Test
    void sanitizeShouldHandleLeadingHtmlComments() {
        final String html = "<!-- site comment --><html><body><p>Hi</p></body></html>";
        final String result = sanitizer.sanitize(html, BASE_URI);
        assertNotNull(result);
        assertTrue(result.contains("Hi"), "body content must be preserved after leading comment");
    }

    @Test
    void sanitizeShouldHandleMissingHtmlStructure() {
        // Bare content with no html/head/body wrapping
        final String result = sanitizer.sanitize("<p>Just a paragraph</p>", BASE_URI);
        assertNotNull(result);
        assertTrue(result.contains("Just a paragraph"), "content must be preserved");
        // Jsoup adds the missing html/head/body structure
        assertTrue(result.contains("<html"), "Jsoup must wrap content in <html>");
    }

    @Test
    void sanitizeShouldPreserveTextContent() {
        final String result = sanitizer.sanitize(
                "<html><body><h1>Title</h1><p>Body text.</p></body></html>", BASE_URI);
        assertTrue(result.contains("Title"));
        assertTrue(result.contains("Body text."));
    }

    @Test
    void sanitizeShouldHandleVoidLinkElement() {
        final String html =
                "<html><head><link rel=\"stylesheet\" href=\"style.css\"></head><body></body></html>";
        final String result = sanitizer.sanitize(html, BASE_URI);
        assertFalse(result.contains("<link rel=\"stylesheet\" href=\"style.css\">"),
                "bare <link> must be self-closed");
        assertTrue(result.contains("style.css"), "href attribute must be preserved");
    }

    // ------------------------------------------------------------------
    // sanitizeForPrint() — print-friendly mode
    // ------------------------------------------------------------------

    @Test
    void sanitizeForPrintShouldThrowWhenHtmlIsNull() {
        assertThrows(NullPointerException.class, () -> sanitizer.sanitizeForPrint(null, BASE_URI));
    }

    @Test
    void sanitizeForPrintShouldThrowWhenBaseUriIsNull() {
        assertThrows(NullPointerException.class,
                () -> sanitizer.sanitizeForPrint("<html></html>", null));
    }

    @Test
    void sanitizeForPrintShouldRemoveScriptElements() {
        final String html = "<html><head><script>alert('xss')</script></head>"
                + "<body><p>Content</p><script>var x=1;</script></body></html>";
        final String result = sanitizer.sanitizeForPrint(html, BASE_URI);
        assertFalse(result.contains("<script"), "all script elements must be removed");
        assertFalse(result.contains("alert"), "script content must not appear in output");
        assertTrue(result.contains("Content"), "body content must be preserved");
    }

    @Test
    void sanitizeForPrintShouldStripFontFaceBlockWithDataUri() {
        final String woff2Data = "data:font/woff2;base64,AAABBBCCC";
        final String html = "<html><head><style>"
                + "@font-face { font-family: 'MyFont'; src: url('" + woff2Data + "') format('woff2'); }"
                + "body { font-size: 14px; }"
                + "</style></head><body><p>Hello</p></body></html>";

        final String result = sanitizer.sanitizeForPrint(html, BASE_URI);

        assertFalse(result.contains("@font-face"), "@font-face with data URI must be removed");
        assertFalse(result.contains(woff2Data), "base64 data URI must not appear in output");
        assertTrue(result.contains("font-size"), "non-font-face CSS rules must be preserved");
        assertTrue(result.contains("Hello"), "body content must be preserved");
    }

    @Test
    void sanitizeForPrintShouldRemoveEntireStyleBlockWhenOnlyFontFaceDataRemains() {
        final String html = "<html><head><style>"
                + "@font-face { font-family: 'X'; src: url('data:font/woff2;base64,AAA'); }"
                + "</style></head><body><p>Text</p></body></html>";

        final String result = sanitizer.sanitizeForPrint(html, BASE_URI);

        assertFalse(result.contains("<style"), "empty style block must be removed entirely");
        assertTrue(result.contains("Text"), "body content must be preserved");
    }

    @Test
    void sanitizeForPrintShouldPreserveNonDataUriFontFace() {
        final String html = "<html><head><style>"
                + "@font-face { font-family: 'MyFont'; src: url('/fonts/myfont.woff2'); }"
                + "</style></head><body><p>Text</p></body></html>";

        final String result = sanitizer.sanitizeForPrint(html, BASE_URI);

        assertTrue(result.contains("@font-face"), "font-face with external URL must not be removed");
        assertTrue(result.contains("myfont.woff2"), "external font URL must be preserved");
    }

    @Test
    void sanitizeForPrintShouldPreserveSemanticContentElements() {
        final String html = "<html><body>"
                + "<h1>Heading</h1><h2>Sub</h2>"
                + "<p>Paragraph with <a href=\"/link\">link</a>.</p>"
                + "<table><tr><td>Cell</td></tr></table>"
                + "<code>code block</code>"
                + "<img src=\"photo.jpg\" alt=\"photo\" />"
                + "</body></html>";

        final String result = sanitizer.sanitizeForPrint(html, BASE_URI);

        assertTrue(result.contains("Heading"), "h1 must be preserved");
        assertTrue(result.contains("Sub"), "h2 must be preserved");
        assertTrue(result.contains("Paragraph"), "paragraph text must be preserved");
        assertTrue(result.contains("link"), "anchor link must be preserved");
        assertTrue(result.contains("Cell"), "table cell must be preserved");
        assertTrue(result.contains("code block"), "code element must be preserved");
        assertTrue(result.contains("photo.jpg"), "img src must be preserved");
    }

    @Test
    void sanitizeForPrintShouldProduceValidXhtmlWithSelfClosedVoidElements() {
        final String html = "<html><body><br><hr><img src=\"a.png\"><p>Text</p></body></html>";
        final String result = sanitizer.sanitizeForPrint(html, BASE_URI);
        // In XHTML all void elements must be self-closing
        assertFalse(result.contains("<br>"), "bare <br> must be self-closed in XHTML output");
        assertFalse(result.contains("<hr>"), "bare <hr> must be self-closed in XHTML output");
        assertTrue(result.contains("Text"), "content must survive");
    }

    // ------------------------------------------------------------------
    // Helper
    // ------------------------------------------------------------------

    private static int countOccurrences(final String text, final String sub) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(sub, idx)) != -1) {
            count++;
            idx += sub.length();
        }
        return count;
    }
}
