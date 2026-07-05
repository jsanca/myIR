package codex.apps.siteexporter;

import java.util.List;
import java.util.Objects;

/**
 * The readable content extracted from a pdf2htmlEX HTML file: a title and an ordered
 * list of {@link ReaderPage}s, each containing paragraph text.
 *
 * <p>Produced by {@link Pdf2HtmlExTextExtractor} and consumed by
 * {@link ReaderHtmlWriter}.</p>
 */
public record ReaderDocument(String title, List<ReaderPage> pages) {

    public ReaderDocument {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(pages, "pages");
        pages = List.copyOf(pages);
    }

    /** Convenience factory for a document with no pages. */
    public static ReaderDocument empty(final String title) {
        Objects.requireNonNull(title, "title");
        return new ReaderDocument(title, List.of());
    }

    /** Total number of paragraph strings across all pages. */
    public int totalParagraphs() {
        return pages.stream().mapToInt(p -> p.paragraphs().size()).sum();
    }
}
