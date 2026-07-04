package codex.apps.siteexporter;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Configuration for a single-page PDF rendering operation.
 *
 * <p>{@code pageSize} is passed directly to OpenHTMLToPDF (e.g. {@code "A4"},
 * {@code "LETTER"}).  {@code baseUri} is the URI used to resolve relative CSS
 * and image references; when {@code null} the renderer derives it from the
 * HTML file's parent directory.</p>
 */
public record PdfRenderOptions(String pageSize, String baseUri) {

    /** Page size used when none is specified. */
    public static final String DEFAULT_PAGE_SIZE = "A4";

    public PdfRenderOptions {
        Objects.requireNonNull(pageSize, "pageSize");
        // baseUri is nullable — renderer derives it from the file path when absent
    }

    /** Returns options with A4 page size and no explicit base URI. */
    public static PdfRenderOptions defaults() {
        return new PdfRenderOptions(DEFAULT_PAGE_SIZE, null);
    }

    /**
     * Returns options whose base URI is the parent directory of {@code htmlFile},
     * so that relative CSS and image references resolve correctly from disk.
     */
    public static PdfRenderOptions forFile(final Path htmlFile) {
        Objects.requireNonNull(htmlFile, "htmlFile");
        final String base = htmlFile.toAbsolutePath().getParent().toUri().toString();
        return new PdfRenderOptions(DEFAULT_PAGE_SIZE, base);
    }
}
