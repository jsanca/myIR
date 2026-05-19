package codex.ir.ingestion.crawler.internal.metadata;

import codex.ir.ingestion.crawler.metadata.PageMetadata;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.Arrays;
import java.util.List;

/**
 * Extracts robots directives from {@code <meta name="robots">} tags.
 */
public final class RobotsMetaExtractor implements DocumentMetadataContributor {

    public List<String> extract(final Document doc) {
        final Element meta = doc.selectFirst("meta[name=robots]");
        if (meta == null) {
            return List.of();
        }
        final String content = meta.attr("content");
        if (content.isBlank()) {
            return List.of();
        }
        return Arrays.stream(content.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
    }

    @Override
    public void contribute(final Document doc, final PageMetadata.Builder builder) {
        builder.robotsDirectives(extract(doc));
    }
}
