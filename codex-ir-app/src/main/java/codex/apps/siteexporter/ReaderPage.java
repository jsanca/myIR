package codex.apps.siteexporter;

import java.util.List;
import java.util.Objects;

/**
 * One page of a {@link ReaderDocument}: an ordered list of paragraph strings
 * extracted from a pdf2htmlEX page frame.
 */
public record ReaderPage(int pageNumber, List<String> paragraphs) {

    public ReaderPage {
        if (pageNumber < 1) {
            throw new IllegalArgumentException("pageNumber must be >= 1, got: " + pageNumber);
        }
        Objects.requireNonNull(paragraphs, "paragraphs");
        paragraphs = List.copyOf(paragraphs);
    }

    /** Returns true if no paragraph text was extracted from this page. */
    public boolean isEmpty() {
        return paragraphs.isEmpty();
    }
}
