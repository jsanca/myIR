package codex.apps.siteexporter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class OpenHtmlToPdfRendererTest {

    @Test
    void renderShouldProducePdfBytesStartingWithMagicHeader(@TempDir final Path tempDir)
            throws Exception {
        final Path htmlFile = tempDir.resolve("page.html");
        Files.writeString(htmlFile, "<html><body><p>Hello PDF</p></body></html>");

        final OpenHtmlToPdfRenderer renderer = new OpenHtmlToPdfRenderer();
        final PdfRenderOptions opts = PdfRenderOptions.forFile(htmlFile);

        final RenderedPdf result = renderer.render(htmlFile, opts);

        assertNotNull(result);
        assertTrue(result.sizeBytes() > 0, "rendered PDF must have non-zero size");

        final byte[] bytes = result.bytes();
        // PDF magic bytes: %PDF
        assertEquals('%', (char) bytes[0]);
        assertEquals('P', (char) bytes[1]);
        assertEquals('D', (char) bytes[2]);
        assertEquals('F', (char) bytes[3]);
    }

    @Test
    void renderShouldUseOptionsBaseUriWhenSet(@TempDir final Path tempDir) throws Exception {
        final Path htmlFile = tempDir.resolve("page.html");
        Files.writeString(htmlFile, "<html><body><p>Hello</p></body></html>");

        final OpenHtmlToPdfRenderer renderer = new OpenHtmlToPdfRenderer();
        final PdfRenderOptions opts = new PdfRenderOptions("A4", tempDir.toUri().toString());

        final RenderedPdf result = renderer.render(htmlFile, opts);

        assertNotNull(result);
        assertTrue(result.sizeBytes() > 0);
        assertEquals(htmlFile, result.sourceFile());
    }

    @Test
    void renderShouldReturnDefensiveCopyOfBytes(@TempDir final Path tempDir) throws Exception {
        final Path htmlFile = tempDir.resolve("page.html");
        Files.writeString(htmlFile, "<html><body><p>Copy test</p></body></html>");

        final OpenHtmlToPdfRenderer renderer = new OpenHtmlToPdfRenderer();
        final RenderedPdf result = renderer.render(htmlFile, PdfRenderOptions.forFile(htmlFile));

        final byte[] first = result.bytes();
        final byte[] second = result.bytes();
        assertNotSame(first, second, "bytes() must return defensive copies");
    }

    @Test
    void renderShouldThrowWhenHtmlFileIsNull() {
        final OpenHtmlToPdfRenderer renderer = new OpenHtmlToPdfRenderer();
        assertThrows(NullPointerException.class,
                () -> renderer.render(null, PdfRenderOptions.defaults()));
    }

    @Test
    void renderShouldThrowWhenOptionsIsNull(@TempDir final Path tempDir) {
        final OpenHtmlToPdfRenderer renderer = new OpenHtmlToPdfRenderer();
        assertThrows(NullPointerException.class,
                () -> renderer.render(tempDir.resolve("page.html"), null));
    }

    @Test
    void renderShouldProducePdfFromHtmlWithBareMetaCharset(@TempDir final Path tempDir)
            throws Exception {
        final Path htmlFile = tempDir.resolve("page.html");
        Files.writeString(htmlFile,
                "<!DOCTYPE html><html><head><meta charset=\"utf-8\"></head>"
                + "<body><p>Charset test</p></body></html>");

        final RenderedPdf result = new OpenHtmlToPdfRenderer()
                .render(htmlFile, PdfRenderOptions.forFile(htmlFile));

        assertPdfMagic(result.bytes());
    }

    @Test
    void renderShouldProducePdfFromHtmlWithBareBrTags(@TempDir final Path tempDir)
            throws Exception {
        final Path htmlFile = tempDir.resolve("page.html");
        Files.writeString(htmlFile,
                "<html><body><p>Line one<br>Line two<br>Line three</p></body></html>");

        final RenderedPdf result = new OpenHtmlToPdfRenderer()
                .render(htmlFile, PdfRenderOptions.forFile(htmlFile));

        assertPdfMagic(result.bytes());
    }

    @Test
    void renderShouldProducePdfFromHtmlWithUnclosedParagraphs(@TempDir final Path tempDir)
            throws Exception {
        final Path htmlFile = tempDir.resolve("page.html");
        Files.writeString(htmlFile,
                "<html><body><p>First paragraph<p>Second paragraph<p>Third</body></html>");

        final RenderedPdf result = new OpenHtmlToPdfRenderer()
                .render(htmlFile, PdfRenderOptions.forFile(htmlFile));

        assertPdfMagic(result.bytes());
    }

    @Test
    void renderShouldProducePdfFromHtmlWithLeadingWhitespaceAndDoctype(@TempDir final Path tempDir)
            throws Exception {
        final Path htmlFile = tempDir.resolve("page.html");
        Files.writeString(htmlFile,
                "   \n\n<!DOCTYPE html>\n<html><body><p>Content</p></body></html>");

        final RenderedPdf result = new OpenHtmlToPdfRenderer()
                .render(htmlFile, PdfRenderOptions.forFile(htmlFile));

        assertPdfMagic(result.bytes());
    }

    @Test
    void renderShouldProducePdfFromHtmlWithMixedVoidElements(@TempDir final Path tempDir)
            throws Exception {
        final Path htmlFile = tempDir.resolve("page.html");
        Files.writeString(htmlFile,
                "<!DOCTYPE html><html><head>"
                + "<meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width\">"
                + "</head><body>"
                + "<h1>Title</h1>"
                + "<p>Para with<br>line break and <img src=\"missing.png\" alt=\"img\"></p>"
                + "<hr>"
                + "</body></html>");

        final RenderedPdf result = new OpenHtmlToPdfRenderer()
                .render(htmlFile, PdfRenderOptions.forFile(htmlFile));

        assertPdfMagic(result.bytes());
    }

    @Test
    void renderShouldProducePdfFromHtmlWithInlinedBase64FontFace(@TempDir final Path tempDir)
            throws Exception {
        // A real-world page pattern: large @font-face with base64 data URI in the style block.
        // sanitizeForPrint must strip it before handing off to OpenHTMLToPDF.
        final String largeBase64 = "A".repeat(2048); // simulates a large font payload
        final Path htmlFile = tempDir.resolve("page.html");
        Files.writeString(htmlFile,
                "<!DOCTYPE html><html><head><style>"
                + "@font-face { font-family:'BigFont'; src:url('data:font/woff2;base64,"
                + largeBase64 + "') format('woff2'); }"
                + "body { font-family: 'BigFont', sans-serif; }"
                + "</style></head><body><h1>Chapter 1</h1><p>Content here.</p></body></html>");

        final RenderedPdf result = new OpenHtmlToPdfRenderer()
                .render(htmlFile, PdfRenderOptions.forFile(htmlFile));

        assertPdfMagic(result.bytes());
    }

    @Test
    void renderShouldProducePdfFromHtmlWithInlineScripts(@TempDir final Path tempDir)
            throws Exception {
        // Scripts must be stripped by sanitizeForPrint without breaking PDF output.
        final Path htmlFile = tempDir.resolve("page.html");
        Files.writeString(htmlFile,
                "<!DOCTYPE html><html><head>"
                + "<script>window.onload = function() { document.title='loaded'; }</script>"
                + "</head><body>"
                + "<p>Paragraph</p>"
                + "<script>var x = 1 < 2 && 3 > 2;</script>"
                + "</body></html>");

        final RenderedPdf result = new OpenHtmlToPdfRenderer()
                .render(htmlFile, PdfRenderOptions.forFile(htmlFile));

        assertPdfMagic(result.bytes());
    }

    @Test
    void renderFailureMessageShouldIncludeFilePathAndRootCause(@TempDir final Path tempDir)
            throws Exception {
        final Path htmlFile = tempDir.resolve("bad-page.html");
        Files.writeString(htmlFile, "<p>content</p>");

        // Inject a sanitizer that returns invalid XML — forces OpenHTMLToPDF to throw.
        final HtmlToXhtmlSanitizer brokenSanitizer = new HtmlToXhtmlSanitizer() {
            @Override
            public String sanitizeForPrint(final String html, final String baseUri) {
                return "<<< deliberately broken xml >>>";
            }
        };

        final OpenHtmlToPdfRenderer renderer = new OpenHtmlToPdfRenderer(brokenSanitizer, null);

        final IOException ex = assertThrows(IOException.class,
                () -> renderer.render(htmlFile, PdfRenderOptions.forFile(htmlFile)));

        final String msg = ex.getMessage();
        assertTrue(msg.contains("bad-page.html"), "error message must include the HTML file name");
        assertNotNull(ex.getCause(), "IOException must wrap the root cause");
        // Root cause message must flow through (not be silently swallowed).
        assertNotNull(ex.getCause().getMessage(), "root cause must have a message");
    }

    @Test
    void renderShouldWriteDebugXhtmlWhenRenderFails(@TempDir final Path tempDir)
            throws Exception {
        final Path htmlFile = tempDir.resolve("fail-page.html");
        Files.writeString(htmlFile, "<p>content</p>");

        final Path debugDir = tempDir.resolve("render-debug");
        // Intentionally do NOT pre-create debugDir — renderer must create it automatically.

        final HtmlToXhtmlSanitizer brokenSanitizer = new HtmlToXhtmlSanitizer() {
            @Override
            public String sanitizeForPrint(final String html, final String baseUri) {
                return "<<< broken xml for debug test >>>";
            }
        };

        final OpenHtmlToPdfRenderer renderer = new OpenHtmlToPdfRenderer(brokenSanitizer, debugDir);

        assertThrows(IOException.class,
                () -> renderer.render(htmlFile, PdfRenderOptions.forFile(htmlFile)));

        final Path debugFile = debugDir.resolve("fail-page.html.xhtml");
        assertTrue(Files.exists(debugFile), "debug XHTML file must be written on render failure");

        final String written = Files.readString(debugFile);
        assertTrue(written.contains("broken xml for debug test"),
                "debug file must contain the exact XHTML that was passed to the renderer");
    }

    @Test
    void renderShouldNotWriteDebugXhtmlOnSuccess(@TempDir final Path tempDir) throws Exception {
        final Path htmlFile = tempDir.resolve("page.html");
        Files.writeString(htmlFile, "<html><body><p>Hello</p></body></html>");

        final Path debugDir = tempDir.resolve("render-debug");

        final OpenHtmlToPdfRenderer renderer = new OpenHtmlToPdfRenderer(debugDir);
        renderer.render(htmlFile, PdfRenderOptions.forFile(htmlFile));

        assertFalse(Files.exists(debugDir), "debug directory must not be created on successful render");
    }

    // ------------------------------------------------------------------
    // Helper
    // ------------------------------------------------------------------

    private static void assertPdfMagic(final byte[] bytes) {
        assertTrue(bytes.length >= 4, "PDF must be at least 4 bytes");
        assertEquals('%', (char) bytes[0]);
        assertEquals('P', (char) bytes[1]);
        assertEquals('D', (char) bytes[2]);
        assertEquals('F', (char) bytes[3]);
    }
}
