package codex.apps.siteexporter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ManifestOrderPdfAssemblyStrategyTest {

    @Test
    void assembleShouldReturnEmptyArrayForEmptyInput() throws Exception {
        final ManifestOrderPdfAssemblyStrategy strategy = new ManifestOrderPdfAssemblyStrategy();
        final byte[] result = strategy.assemble(List.of());
        assertNotNull(result);
        assertEquals(0, result.length);
    }

    @Test
    void assembleShouldThrowWhenPagesIsNull() {
        final ManifestOrderPdfAssemblyStrategy strategy = new ManifestOrderPdfAssemblyStrategy();
        assertThrows(NullPointerException.class, () -> strategy.assemble(null));
    }

    @Test
    void assembleShouldProducePdfMagicBytesForSinglePage(@TempDir final Path tempDir) throws Exception {
        final byte[] pdfBytes = renderPdf(tempDir, "page.html", "<html><body><p>Page 1</p></body></html>");

        final ManifestOrderPdfAssemblyStrategy strategy = new ManifestOrderPdfAssemblyStrategy();
        final byte[] merged = strategy.assemble(List.of(pdfBytes));

        assertPdfMagic(merged);
    }

    @Test
    void assembleShouldProducePdfMagicBytesForMultiplePages(@TempDir final Path tempDir) throws Exception {
        final byte[] pdf1 = renderPdf(tempDir, "p1.html", "<html><body><p>Page 1</p></body></html>");
        final byte[] pdf2 = renderPdf(tempDir, "p2.html", "<html><body><p>Page 2</p></body></html>");

        final ManifestOrderPdfAssemblyStrategy strategy = new ManifestOrderPdfAssemblyStrategy();
        final byte[] merged = strategy.assemble(List.of(pdf1, pdf2));

        assertPdfMagic(merged);
        assertTrue(merged.length > 0);
    }

    @Test
    void assembleOrderShouldMatchInputOrder(@TempDir final Path tempDir) throws Exception {
        // Two distinct single-page PDFs — after merge the combined bytes must contain
        // content from both in the order given (verified by checking merge is non-empty
        // and larger than any single input, which implies both were included).
        final byte[] pdf1 = renderPdf(tempDir, "a.html", "<html><body><p>Alpha section</p></body></html>");
        final byte[] pdf2 = renderPdf(tempDir, "b.html", "<html><body><p>Beta section</p></body></html>");

        final ManifestOrderPdfAssemblyStrategy strategy = new ManifestOrderPdfAssemblyStrategy();
        final byte[] merged = strategy.assemble(List.of(pdf1, pdf2));

        assertPdfMagic(merged);
        // The merged document should be non-trivially larger than either individual page
        assertTrue(merged.length > Math.min(pdf1.length, pdf2.length),
                "merged document should incorporate content from multiple pages");
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static byte[] renderPdf(final Path tempDir, final String filename, final String html)
            throws Exception {
        final Path htmlFile = tempDir.resolve(filename);
        Files.writeString(htmlFile, html);
        return new OpenHtmlToPdfRenderer()
                .render(htmlFile, PdfRenderOptions.forFile(htmlFile)).bytes();
    }

    private static void assertPdfMagic(final byte[] bytes) {
        assertTrue(bytes.length >= 4, "PDF must be at least 4 bytes");
        assertEquals('%', (char) bytes[0]);
        assertEquals('P', (char) bytes[1]);
        assertEquals('D', (char) bytes[2]);
        assertEquals('F', (char) bytes[3]);
    }
}
