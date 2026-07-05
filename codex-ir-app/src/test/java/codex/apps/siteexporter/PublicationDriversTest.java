package codex.apps.siteexporter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PublicationDriversTest {

    // ------------------------------------------------------------------
    // Null guards
    // ------------------------------------------------------------------

    @Test
    void forFormatShouldThrowWhenFormatIsNull(@TempDir final Path tempDir) {
        assertThrows(NullPointerException.class,
                () -> PublicationDrivers.forFormat(null, tempDir));
    }

    @Test
    void forFormatShouldThrowWhenOutputDirIsNull() {
        assertThrows(NullPointerException.class,
                () -> PublicationDrivers.forFormat(PublicationFormat.PDF, null));
    }

    // ------------------------------------------------------------------
    // Factory returns correct driver type
    // ------------------------------------------------------------------

    @Test
    void forFormatShouldReturnPdfDriverForPdf(@TempDir final Path tempDir) {
        final PublicationDriver driver = PublicationDrivers.forFormat(
                PublicationFormat.PDF, tempDir);

        assertInstanceOf(PdfPublicationDriver.class, driver,
                "forFormat(PDF) must return a PdfPublicationDriver");
    }

    @Test
    void forFormatShouldReturnMarkdownDriverForMarkdown(@TempDir final Path tempDir) {
        final PublicationDriver driver = PublicationDrivers.forFormat(
                PublicationFormat.MARKDOWN, tempDir);

        assertInstanceOf(MarkdownPublicationDriver.class, driver,
                "forFormat(MARKDOWN) must return a MarkdownPublicationDriver");
    }

    @Test
    void forFormatShouldThrowForEpub(@TempDir final Path tempDir) {
        final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> PublicationDrivers.forFormat(PublicationFormat.EPUB, tempDir));

        final String msg = ex.getMessage();
        assertTrue(msg.toUpperCase().contains("EPUB"),
                "error message must mention EPUB");
        assertTrue(msg.contains("PDF") || msg.contains("MARKDOWN"),
                "error message must suggest a supported alternative");
    }

    // ------------------------------------------------------------------
    // Driver contract: requiresAssetProcessing
    // ------------------------------------------------------------------

    @Test
    void pdfDriverShouldRequireAssetProcessing(@TempDir final Path tempDir) {
        assertTrue(PublicationDrivers.forFormat(PublicationFormat.PDF, tempDir)
                .requiresAssetProcessing());
    }

    @Test
    void markdownDriverShouldNotRequireAssetProcessing(@TempDir final Path tempDir) {
        assertFalse(PublicationDrivers.forFormat(PublicationFormat.MARKDOWN, tempDir)
                .requiresAssetProcessing());
    }
}
