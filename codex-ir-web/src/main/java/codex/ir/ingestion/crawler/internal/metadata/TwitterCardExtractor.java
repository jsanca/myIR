package codex.ir.ingestion.crawler.internal.metadata;

import codex.ir.ingestion.crawler.metadata.PageMetadata;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Extracts Twitter Card metadata from {@code <meta name="twitter:*">} tags.
 */
public final class TwitterCardExtractor implements DocumentMetadataContributor {

    public Map<String, String> extract(final Document doc) {
        final Map<String, String> result = new LinkedHashMap<>();
        for (final Element meta : doc.select("meta[name^=twitter:]")) {
            final String name = meta.attr("name").trim();
            final String content = meta.attr("content").trim();
            if (!name.isBlank() && !content.isBlank()) {
                result.put(name, content);
            }
        }
        return Map.copyOf(result);
    }

    @Override
    public void contribute(final Document doc, final PageMetadata.Builder builder) {
        builder.twitterCard(extract(doc));
    }
}
