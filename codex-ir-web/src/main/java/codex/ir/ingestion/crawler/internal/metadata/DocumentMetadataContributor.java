package codex.ir.ingestion.crawler.internal.metadata;

import codex.ir.ingestion.crawler.metadata.PageMetadata;
import org.jsoup.nodes.Document;

/**
 * Populates one or more fields of a {@link PageMetadata.Builder} from an HTML document.
 */
@FunctionalInterface
public interface DocumentMetadataContributor {

    void contribute(Document doc, PageMetadata.Builder builder);
}
