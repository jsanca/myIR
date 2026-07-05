package codex.apps.siteexporter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ReadablePageExtractorTest {

    private final ReadablePageExtractor extractor = new ReadablePageExtractor();

    // ------------------------------------------------------------------
    // Null / missing file guards
    // ------------------------------------------------------------------

    @Test
    void extractShouldThrowWhenHtmlFileIsNull() {
        assertThrows(NullPointerException.class, () -> extractor.extract(null));
    }

    @Test
    void extractShouldThrowWhenHtmlFileDoesNotExist(@TempDir final Path tempDir) {
        assertThrows(Exception.class,
                () -> extractor.extract(tempDir.resolve("nonexistent.html")));
    }

    // ------------------------------------------------------------------
    // pdf2htmlEX routing
    // ------------------------------------------------------------------

    @Test
    void extractShouldRoutePdf2HtmlExPagesAndReturnExpectedStrings(@TempDir final Path tempDir)
            throws Exception {
        final Path htmlFile = tempDir.resolve("chapter.html");
        Files.writeString(htmlFile, Pdf2HtmlExDetectorTest.loadFixture());

        final ReaderDocument doc = extractor.extract(htmlFile);

        final String allText = doc.pages().stream()
                .flatMap(p -> p.paragraphs().stream())
                .reduce("", (a, b) -> a + " " + b);

        assertTrue(allText.contains("Part I"),
                "extracted text must contain 'Part I' from pdf2htmlEX fixture");
        assertTrue(allText.contains("Applied Math and Machine Learning Basics"),
                "extracted text must contain subtitle");
        assertTrue(allText.contains("This part of the book introduces"),
                "extracted text must contain body paragraph");
    }

    @Test
    void extractShouldReturnMultiplePagesForPdf2HtmlExDocument(@TempDir final Path tempDir)
            throws Exception {
        final Path htmlFile = tempDir.resolve("chapter.html");
        Files.writeString(htmlFile, Pdf2HtmlExDetectorTest.loadFixture());

        final ReaderDocument doc = extractor.extract(htmlFile);

        assertFalse(doc.pages().isEmpty(),
                "pdf2htmlEX document must have at least one page");
    }

    // ------------------------------------------------------------------
    // Normal HTML extraction
    // ------------------------------------------------------------------

    @Test
    void extractShouldExtractParagraphsFromNormalHtml(@TempDir final Path tempDir)
            throws Exception {
        final Path htmlFile = tempDir.resolve("page.html");
        Files.writeString(htmlFile,
                "<html><head><title>Test</title></head>"
                + "<body><p>Hello world</p><p>Second paragraph</p></body></html>");

        final ReaderDocument doc = extractor.extract(htmlFile);

        final String allText = doc.pages().stream()
                .flatMap(p -> p.paragraphs().stream())
                .reduce("", (a, b) -> a + " " + b);
        assertTrue(allText.contains("Hello world"), "first paragraph must be extracted");
        assertTrue(allText.contains("Second paragraph"), "second paragraph must be extracted");
    }

    @Test
    void extractShouldExtractHeadingsFromNormalHtml(@TempDir final Path tempDir)
            throws Exception {
        final Path htmlFile = tempDir.resolve("page.html");
        Files.writeString(htmlFile,
                "<html><body><h1>Chapter Title</h1><p>Body text.</p></body></html>");

        final ReaderDocument doc = extractor.extract(htmlFile);

        final String allText = doc.pages().stream()
                .flatMap(p -> p.paragraphs().stream())
                .reduce("", (a, b) -> a + " " + b);
        assertTrue(allText.contains("Chapter Title"), "heading must be extracted");
        assertTrue(allText.contains("Body text."), "paragraph must be extracted");
    }

    @Test
    void extractShouldExtractListItemsFromNormalHtml(@TempDir final Path tempDir)
            throws Exception {
        final Path htmlFile = tempDir.resolve("page.html");
        Files.writeString(htmlFile,
                "<html><body><ul><li>Item one</li><li>Item two</li></ul></body></html>");

        final ReaderDocument doc = extractor.extract(htmlFile);

        final String allText = doc.pages().stream()
                .flatMap(p -> p.paragraphs().stream())
                .reduce("", (a, b) -> a + " " + b);
        assertTrue(allText.contains("Item one"), "list items must be extracted");
        assertTrue(allText.contains("Item two"));
    }

    @Test
    void extractShouldReturnSinglePageForNormalHtml(@TempDir final Path tempDir)
            throws Exception {
        final Path htmlFile = tempDir.resolve("page.html");
        Files.writeString(htmlFile, "<html><body><p>Some text</p></body></html>");

        final ReaderDocument doc = extractor.extract(htmlFile);

        assertEquals(1, doc.pages().size(),
                "normal HTML must produce exactly one page");
    }

    // ------------------------------------------------------------------
    // extractNormalHtml (static, package-visible)
    // ------------------------------------------------------------------

    @Test
    void extractNormalHtmlShouldUseTitleFromDocument() {
        final ReaderDocument doc = ReadablePageExtractor.extractNormalHtml(
                "<html><head><title>My Title</title></head><body><p>text</p></body></html>",
                "http://example.com/");

        assertEquals("My Title", doc.title());
    }

    @Test
    void extractNormalHtmlShouldFallBackToDocumentDefaultWhenTitleIsBlank() {
        final ReaderDocument doc = ReadablePageExtractor.extractNormalHtml(
                "<html><body><p>text</p></body></html>",
                "http://example.com/");

        assertEquals("Document", doc.title(),
                "blank <title> must fall back to 'Document'");
    }

    @Test
    void extractNormalHtmlShouldFallBackToBodyTextWhenNoStructuralElements() {
        final ReaderDocument doc = ReadablePageExtractor.extractNormalHtml(
                "<html><body>Plain text with no tags</body></html>",
                "http://example.com/");

        assertFalse(doc.pages().isEmpty(), "fallback body text must produce a page");
        assertTrue(doc.pages().get(0).paragraphs().stream()
                        .anyMatch(p -> p.contains("Plain text with no tags")),
                "body text must be captured in fallback mode");
    }

    @Test
    void extractNormalHtmlShouldReturnEmptyDocumentForBodylessHtml() {
        final ReaderDocument doc = ReadablePageExtractor.extractNormalHtml(
                "<html></html>",
                "http://example.com/");

        assertTrue(doc.pages().isEmpty() || doc.pages().stream().allMatch(
                p -> p.paragraphs().isEmpty()),
                "bodyless HTML must produce an empty document");
    }
}
