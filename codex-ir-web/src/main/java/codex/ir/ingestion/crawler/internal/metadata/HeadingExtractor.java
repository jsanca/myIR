package codex.ir.ingestion.crawler.internal.metadata;

import codex.ir.ingestion.crawler.metadata.PageMetadata;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.List;

/**
 * Extracts h1 and h2 heading text from an HTML document.
 */
public final class HeadingExtractor implements DocumentMetadataContributor {

    /** H1 and H2 headings extracted from the document body. */
    public record Headings(List<String> h1, List<String> h2) {
    }

    public Headings extract(final Document doc) {
        return new Headings(headings(doc, "h1"), headings(doc, "h2"));
    }

    @Override
    public void contribute(final Document doc, final PageMetadata.Builder builder) {
        final Headings headings = extract(doc);
        builder.h1Headings(headings.h1());
        builder.h2Headings(headings.h2());
    }

    private static List<String> headings(final Document doc, final String tag) {
        return doc.select(tag).stream()
                .map(Element::text)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
    }
}
