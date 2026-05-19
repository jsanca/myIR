package codex.ir.ingestion.crawler.internal.metadata;

import codex.ir.ingestion.crawler.metadata.PageMetadata;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.Optional;

/**
 * Extracts standard head meta fields: title, description, canonical URL.
 */
public final class MetaTagExtractor implements DocumentMetadataContributor {

    /** Title, description, and canonical URL extracted from the document head. */
    public record StandardMetaTags(
            Optional<String> title,
            Optional<String> description,
            Optional<String> canonicalUrl
    ) {
    }

    public StandardMetaTags extract(final Document doc) {
        return new StandardMetaTags(
                extractTitle(doc),
                extractDescription(doc),
                extractCanonicalUrl(doc)
        );
    }

    @Override
    public void contribute(final Document doc, final PageMetadata.Builder builder) {
        final StandardMetaTags tags = extract(doc);
        tags.title().ifPresent(builder::title);
        tags.description().ifPresent(builder::description);
        tags.canonicalUrl().ifPresent(builder::canonicalUrl);
    }

    private static Optional<String> extractTitle(final Document doc) {
        final String title = doc.title();
        if (title != null && !title.isBlank()) {
            return Optional.of(title.trim());
        }
        return Optional.empty();
    }

    private static Optional<String> extractDescription(final Document doc) {
        final Element meta = doc.selectFirst("meta[name=description]");
        if (meta != null) {
            final String content = meta.attr("content").trim();
            if (!content.isBlank()) {
                return Optional.of(content);
            }
        }
        return Optional.empty();
    }

    private static Optional<String> extractCanonicalUrl(final Document doc) {
        final Element link = doc.selectFirst("link[rel=canonical]");
        if (link != null) {
            final String href = link.attr("href").trim();
            if (!href.isBlank()) {
                return Optional.of(href);
            }
        }
        return Optional.empty();
    }
}
