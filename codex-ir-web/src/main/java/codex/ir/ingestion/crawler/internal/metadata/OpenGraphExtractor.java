package codex.ir.ingestion.crawler.internal.metadata;

import codex.ir.ingestion.crawler.metadata.PageMetadata;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Extracts Open Graph metadata from {@code <meta property="og:*">} tags.
 */
public final class OpenGraphExtractor implements DocumentMetadataContributor {

    public Map<String, String> extract(final Document doc) {
        final Map<String, String> result = new LinkedHashMap<>();
        for (final Element meta : doc.select("meta[property^=og:]")) {
            final String property = meta.attr("property").trim();
            final String content = meta.attr("content").trim();
            if (!property.isBlank() && !content.isBlank()) {
                result.put(property, content);
            }
        }
        return Map.copyOf(result);
    }

    @Override
    public void contribute(final Document doc, final PageMetadata.Builder builder) {
        builder.openGraph(extract(doc));
    }
}
