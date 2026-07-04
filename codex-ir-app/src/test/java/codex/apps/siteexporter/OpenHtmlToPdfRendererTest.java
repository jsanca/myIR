package codex.apps.siteexporter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
}
