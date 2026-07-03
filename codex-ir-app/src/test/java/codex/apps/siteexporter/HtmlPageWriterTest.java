package codex.apps.siteexporter;

import codex.ir.ingestion.WebPage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HtmlPageWriterTest {

    @Test
    void shouldWriteHtmlContentToResolvedPath(@TempDir final Path tempDir) throws Exception {
        final HtmlPageWriter writer = new HtmlPageWriter(new LocalPathResolver(tempDir));
        final WebPage page = page("https://example.com/about", "<html><body>About</body></html>");

        final Path written = writer.write(page);

        assertTrue(Files.exists(written));
        assertEquals("<html><body>About</body></html>", Files.readString(written));
        assertEquals(tempDir.resolve("about/index.html"), written);
    }

    @Test
    void shouldCreateMissingParentDirectories(@TempDir final Path tempDir) throws Exception {
        final HtmlPageWriter writer = new HtmlPageWriter(new LocalPathResolver(tempDir));
        final WebPage page = page("https://example.com/deep/nested/page", "<html>Deep</html>");

        final Path written = writer.write(page);

        assertTrue(Files.exists(written));
        assertTrue(Files.isDirectory(tempDir.resolve("deep/nested/page")));
    }

    @Test
    void shouldWriteEmptyStringWhenRawHtmlIsNull(@TempDir final Path tempDir) throws Exception {
        final HtmlPageWriter writer = new HtmlPageWriter(new LocalPathResolver(tempDir));
        final WebPage page = new WebPage(
                URI.create("https://example.com/"),
                null, null, null, 200, "text/html", Instant.now(), Map.of());

        final Path written = writer.write(page);

        assertTrue(Files.exists(written));
        assertEquals("", Files.readString(written));
    }

    @Test
    void shouldOverwriteExistingFile(@TempDir final Path tempDir) throws Exception {
        final HtmlPageWriter writer = new HtmlPageWriter(new LocalPathResolver(tempDir));
        final URI uri = URI.create("https://example.com/");

        writer.write(page(uri.toString(), "<html>First</html>"));
        final Path written = writer.write(page(uri.toString(), "<html>Second</html>"));

        assertEquals("<html>Second</html>", Files.readString(written));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static WebPage page(final String url, final String html) {
        return new WebPage(URI.create(url), html, "Title", "Body text",
                200, "text/html", Instant.now(), Map.of());
    }
}
