package codex.ir.ingestion.crawler;

import codex.ir.ingestion.WebPage;
import codex.ir.ingestion.WebCrawlingConfig;

import java.net.URI;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Abstraction responsible for turning a target URI into a fetched {@link WebPage}.
 *
 * <p>This interface lives one level above the low-level HTTP transport layer.
 * While a {@code WebHttpFetcher} is responsible only for downloading an HTTP
 * resource, a {@code WebPageFetcher} is responsible for producing the richer
 * {@link WebPage} model used by the ingestion pipeline.</p>
 *
 * <p>Implementations may use different strategies to build a web page:
 * a fast path based on plain HTTP + HTML parsing, or a heavier path based on
 * browser automation for JavaScript-heavy sites.</p>
 * @author jsanca & elo
 */
@FunctionalInterface
public interface WebPageFetcher {


    /**
     * Fetches and builds a {@link WebPage} for the given URI.
     *
     * @param uri target URI to fetch
     * @return fetched and parsed web page representation
     */
    Optional<WebPage> fetch(URI uri, Consumer<Set<URI>> consumer);
}