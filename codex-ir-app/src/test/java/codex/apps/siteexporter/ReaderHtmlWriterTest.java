package codex.apps.siteexporter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReaderHtmlWriterTest {

    private final ReaderHtmlWriter writer = new ReaderHtmlWriter();

    // ------------------------------------------------------------------
    // Null guard
    // ------------------------------------------------------------------

    @Test
    void writeShouldThrowWhenDocumentIsNull() {
        assertThrows(NullPointerException.class, () -> writer.write(null));
    }

    // ------------------------------------------------------------------
    // Structure tests
    // ------------------------------------------------------------------

    @Test
    void writeShouldIncludeTitleFromDocument() {
        final ReaderDocument doc = new ReaderDocument("My Book", List.of());
        final String html = writer.write(doc);
        assertTrue(html.contains("<title>My Book</title>"), "title must appear in <head>");
    }

    @Test
    void writeShouldProduceOnePageDivPerReaderPage() {
        final ReaderDocument doc = new ReaderDocument("Test",
                List.of(new ReaderPage(1, List.of("Para A")),
                        new ReaderPage(2, List.of("Para B"))));
        final String html = writer.write(doc);
        final int count = countOccurrences(html, "class=\"page\"");
        assertEquals(2, count, "one .page div per ReaderPage");
    }

    @Test
    void writeShouldWrapEachParagraphInPTag() {
        final ReaderDocument doc = new ReaderDocument("Test",
                List.of(new ReaderPage(1, List.of("First", "Second", "Third"))));
        final String html = writer.write(doc);
        final int pTags = countOccurrences(html, "<p>");
        assertEquals(3, pTags, "each paragraph must be wrapped in a <p> element");
        assertTrue(html.contains("<p>First</p>"));
        assertTrue(html.contains("<p>Second</p>"));
        assertTrue(html.contains("<p>Third</p>"));
    }

    @Test
    void writeShouldContainPageNumberMarker() {
        final ReaderDocument doc = new ReaderDocument("Test",
                List.of(new ReaderPage(7, List.of("Content"))));
        final String html = writer.write(doc);
        assertTrue(html.contains("7"), "page number must appear in the marker");
        assertTrue(html.contains("class=\"page-num\""), ".page-num marker must be present");
    }

    @Test
    void writeShouldEscapeAmpersandsInParagraphText() {
        final ReaderDocument doc = new ReaderDocument("Test",
                List.of(new ReaderPage(1, List.of("AT&T"))));
        final String html = writer.write(doc);
        assertTrue(html.contains("AT&amp;T"), "& must be escaped as &amp;");
        assertFalse(html.contains("<p>AT&T</p>"), "unescaped & must not appear in output");
    }

    @Test
    void writeShouldEscapeLessThanAndGreaterThanInParagraphText() {
        final ReaderDocument doc = new ReaderDocument("Test",
                List.of(new ReaderPage(1, List.of("a < b > c"))));
        final String html = writer.write(doc);
        assertTrue(html.contains("a &lt; b &gt; c"), "< and > must be escaped");
    }

    @Test
    void writeShouldEscapeAmpersandInTitle() {
        final ReaderDocument doc = new ReaderDocument("Math & Science", List.of());
        final String html = writer.write(doc);
        assertTrue(html.contains("<title>Math &amp; Science</title>"),
                "& in title must be escaped");
    }

    @Test
    void writeShouldContainNoScriptElements() {
        final ReaderDocument doc = new ReaderDocument("Safe",
                List.of(new ReaderPage(1, List.of("Clean text"))));
        final String html = writer.write(doc);
        assertFalse(html.contains("<script"), "output must contain no script elements");
    }

    @Test
    void writeShouldContainNoAtFontFaceDeclarations() {
        final ReaderDocument doc = new ReaderDocument("Test",
                List.of(new ReaderPage(1, List.of("Text"))));
        final String html = writer.write(doc);
        assertFalse(html.toLowerCase().contains("@font-face"),
                "output must not include @font-face declarations");
    }

    @Test
    void writeShouldProduceNonEmptyStringForEmptyPageList() {
        final ReaderDocument doc = ReaderDocument.empty("Empty");
        final String html = writer.write(doc);
        assertFalse(html.isBlank(), "even a document with no pages must produce non-empty HTML");
        assertTrue(html.contains("<title>Empty</title>"));
    }

    // ------------------------------------------------------------------
    // End-to-end rendering test
    // ------------------------------------------------------------------

    @Test
    void writerOutputShouldRenderToValidPdfViaOpenHtmlToPdf(@TempDir final Path tempDir)
            throws Exception {
        // Build a multi-page ReaderDocument from the fixture
        final String fixtureHtml = Pdf2HtmlExDetectorTest.loadFixture();
        final ReaderDocument doc = new Pdf2HtmlExTextExtractor()
                .extract(fixtureHtml, "https://example.com/");

        final String readerHtml = writer.write(doc);

        final Path htmlFile = tempDir.resolve("reader.html");
        Files.writeString(htmlFile, readerHtml);

        final RenderedPdf pdf = new OpenHtmlToPdfRenderer()
                .render(htmlFile, PdfRenderOptions.forFile(htmlFile));

        final byte[] bytes = pdf.bytes();
        assertTrue(bytes.length >= 4, "rendered PDF must have non-zero size");
        assertEquals('%', (char) bytes[0]);
        assertEquals('P', (char) bytes[1]);
        assertEquals('D', (char) bytes[2]);
        assertEquals('F', (char) bytes[3]);
    }

    // ------------------------------------------------------------------
    // Fixture-based content test
    // ------------------------------------------------------------------

    @Test
    void writerOutputShouldContainAllValidationStrings() throws Exception {
        final String fixtureHtml = Pdf2HtmlExDetectorTest.loadFixture();
        final ReaderDocument doc = new Pdf2HtmlExTextExtractor()
                .extract(fixtureHtml, "https://example.com/");

        final String html = writer.write(doc);

        assertTrue(html.contains("Part I"),
                "rendered HTML must contain 'Part I'");
        assertTrue(html.contains("Applied Math and Machine Learning Basics"),
                "rendered HTML must contain subtitle");
        assertTrue(html.contains("This part of the book introduces"),
                "rendered HTML must contain body text prefix");
    }

    // ------------------------------------------------------------------
    // Helper
    // ------------------------------------------------------------------

    private static int countOccurrences(final String text, final String sub) {
        int count = 0, idx = 0;
        while ((idx = text.indexOf(sub, idx)) != -1) {
            count++;
            idx += sub.length();
        }
        return count;
    }
}
