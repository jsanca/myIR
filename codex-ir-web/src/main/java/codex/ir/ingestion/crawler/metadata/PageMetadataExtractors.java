package codex.ir.ingestion.crawler.metadata;

import codex.ir.ingestion.WebPage;
import codex.ir.ingestion.crawler.internal.metadata.DocumentMetadataContributor;
import codex.ir.ingestion.crawler.internal.metadata.HeadingExtractor;
import codex.ir.ingestion.crawler.internal.metadata.JsonLdBlockExtractor;
import codex.ir.ingestion.crawler.internal.metadata.MetaTagExtractor;
import codex.ir.ingestion.crawler.internal.metadata.OpenGraphExtractor;
import codex.ir.ingestion.crawler.internal.metadata.RobotsMetaExtractor;
import codex.ir.ingestion.crawler.internal.metadata.TwitterCardExtractor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Factory for {@link PageMetadataExtractor} implementations.
 */
public final class PageMetadataExtractors {

    private PageMetadataExtractors() {
    }

    /**
     * Returns a Jsoup-based extractor covering all supported metadata fields.
     */
    public static PageMetadataExtractor jsoupDefault() {
        return new JsoupPageMetadataExtractor(List.of(
                new MetaTagExtractor(),
                new OpenGraphExtractor(),
                new TwitterCardExtractor(),
                new RobotsMetaExtractor(),
                new HeadingExtractor(),
                new JsonLdBlockExtractor()
        ));
    }

    private static final class JsoupPageMetadataExtractor implements PageMetadataExtractor {

        private static final Logger LOGGER = LoggerFactory.getLogger(JsoupPageMetadataExtractor.class);

        private final List<DocumentMetadataContributor> contributors;

        private JsoupPageMetadataExtractor(final List<DocumentMetadataContributor> contributors) {
            this.contributors = Objects.requireNonNull(contributors);
        }

        @Override
        public PageMetadata extract(final WebPage page) {
            Objects.requireNonNull(page, "page must not be null");
            final String html = page.rawHtml();
            if (html == null || html.isBlank()) {
                return PageMetadata.empty();
            }

            final Document doc;
            try {
                doc = Jsoup.parse(html, page.url().toString());
            } catch (final Exception exception) {
                LOGGER.debug("Failed to parse HTML for {}", page.url(), exception);
                return PageMetadata.empty();
            }

            final PageMetadata.Builder builder = PageMetadata.builder();
            for (final DocumentMetadataContributor contributor : contributors) {
                contributor.contribute(doc, builder);
            }
            extractLanguage(doc).ifPresent(builder::language);
            return builder.build();
        }

        private static Optional<String> extractLanguage(final Document doc) {
            final String lang = doc.select("html[lang]").attr("lang").trim();
            return lang.isBlank() ? Optional.empty() : Optional.of(lang);
        }
    }
}
