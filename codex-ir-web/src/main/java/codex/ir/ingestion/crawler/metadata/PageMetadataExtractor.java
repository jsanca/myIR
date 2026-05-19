package codex.ir.ingestion.crawler.metadata;

import codex.ir.ingestion.WebPage;

/**
 * Extracts {@link PageMetadata} from a fetched {@link WebPage}.
 */
@FunctionalInterface
public interface PageMetadataExtractor {

    /**
     * Extracts page metadata from the given web page.
     *
     * @param page the fetched web page
     * @return extracted metadata; absent fields are empty or empty collections
     */
    PageMetadata extract(WebPage page);
}
