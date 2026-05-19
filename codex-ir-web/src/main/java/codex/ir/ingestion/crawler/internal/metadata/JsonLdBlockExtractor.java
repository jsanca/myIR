package codex.ir.ingestion.crawler.internal.metadata;

import codex.ir.ingestion.crawler.metadata.PageMetadata;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.List;

/**
 * Extracts raw JSON-LD script blocks from an HTML document.
 */
public final class JsonLdBlockExtractor implements DocumentMetadataContributor {

    public List<String> extract(final Document doc) {
        final List<String> result = new ArrayList<>();
        for (final Element script : doc.select("script[type=application/ld+json]")) {
            final String json = script.html();
            if (json != null && !json.isBlank()) {
                result.add(json.trim());
            }
        }
        return List.copyOf(result);
    }

    @Override
    public void contribute(final Document doc, final PageMetadata.Builder builder) {
        builder.jsonLdBlocks(extract(doc));
    }
}
