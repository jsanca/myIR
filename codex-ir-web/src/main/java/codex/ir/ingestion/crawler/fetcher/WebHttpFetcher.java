package codex.ir.ingestion.crawler.fetcher;

import java.net.URI;

/**
 * Abstraction for fetching HTTP resources used by the crawling layer.
 *
 * <p>This interface hides the underlying HTTP client implementation (e.g., JDK HttpClient,
 * OkHttp, etc.) and provides a minimal contract focused on retrieving web resources.</p>
 *
 * <p>Implementations are responsible for executing the request and returning a
 * {@link WebHttpResponse} with the relevant information.</p>
 * @author jsanca & elo
 */
@FunctionalInterface
public interface WebHttpFetcher {

    /**
     * Fetches the resource located at the given URI.
     *
     * @param uri the target URI
     * @return the HTTP response
     */
    WebHttpResponse fetch(URI uri);
}
