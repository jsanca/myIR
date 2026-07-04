package codex.apps.siteexporter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PdfBoxMergeStrategyTest {

    @Test
    void assembleShouldReturnEmptyArrayForEmptyInput() throws Exception {
        final PdfBoxMergeStrategy strategy = new PdfBoxMergeStrategy();
        final byte[] result = strategy.assemble(List.of());
        assertNotNull(result);
        assertEquals(0, result.length);
    }

    @Test
    void assembleShouldThrowWhenPagesIsNull() {
        final PdfBoxMergeStrategy strategy = new PdfBoxMergeStrategy();
        assertThrows(NullPointerException.class, () -> strategy.assemble(null));
    }

    @Test
    void assembleShouldProducePdfMagicBytesForSinglePage(@TempDir final Path tempDir) throws Exception {
        final byte[] pdfBytes = renderPdf(tempDir, "page.html", "<html><body><p>Page 1</p></body></html>");

        final PdfBoxMergeStrategy strategy = new PdfBoxMergeStrategy();
        final byte[] merged = strategy.assemble(List.of(pdfBytes));

        assertPdfMagic(merged);
    }

    @Test
    void assembleShouldProducePdfMagicBytesForMultiplePages(@TempDir final Path tempDir) throws Exception {
        final byte[] pdf1 = renderPdf(tempDir, "p1.html", "<html><body><p>Page 1</p></body></html>");
        final byte[] pdf2 = renderPdf(tempDir, "p2.html", "<html><body><p>Page 2</p></body></html>");
        final byte[] pdf3 = renderPdf(tempDir, "p3.html", "<html><body><p>Page 3</p></body></html>");

        final PdfBoxMergeStrategy strategy = new PdfBoxMergeStrategy();
        final byte[] merged = strategy.assemble(List.of(pdf1, pdf2, pdf3));

        assertPdfMagic(merged);
        assertTrue(merged.length > 0, "merged PDF must be non-empty");
    }

    @Test
    void mergedPdfShouldBeLargerThanAnyIndividualPage(@TempDir final Path tempDir) throws Exception {
        final byte[] pdf1 = renderPdf(tempDir, "a.html", "<html><body><p>Alpha</p></body></html>");
        final byte[] pdf2 = renderPdf(tempDir, "b.html", "<html><body><p>Beta content here</p></body></html>");

        final PdfBoxMergeStrategy strategy = new PdfBoxMergeStrategy();
        final byte[] merged = strategy.assemble(List.of(pdf1, pdf2));

        assertTrue(merged.length > pdf1.length || merged.length > pdf2.length,
                "merged PDF should not be smaller than both individual pages");
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static byte[] renderPdf(final Path tempDir, final String filename, final String html)
            throws Exception {
        final Path htmlFile = tempDir.resolve(filename);
        Files.writeString(htmlFile, html);
        final OpenHtmlToPdfRenderer renderer = new OpenHtmlToPdfRenderer();
        return renderer.render(htmlFile, PdfRenderOptions.forFile(htmlFile)).bytes();
    }

    private static void assertPdfMagic(final byte[] bytes) {
        assertTrue(bytes.length >= 4, "PDF must be at least 4 bytes");
        assertEquals('%', (char) bytes[0]);
        assertEquals('P', (char) bytes[1]);
        assertEquals('D', (char) bytes[2]);
        assertEquals('F', (char) bytes[3]);
    }
}
