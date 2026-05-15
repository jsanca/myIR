package codex.ir.ingestion.crawler.fetcher;

import codex.ir.ingestion.WebCrawlingConfig;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;

/**
 * Factory and container for {@link WebHttpFetcher} implementations.
 *
 * <p>This class centralizes creation of HTTP fetchers used by the crawling
 * layer while hiding the concrete transport implementation behind the
 * {@link WebHttpFetcher} abstraction.</p>
 * @author jsanca & elo
 */
public final class WebHttpFetchers {

    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private WebHttpFetchers() {
    }

    /**
     * Creates the default JDK-based HTTP fetcher.
     *
     * @return fetcher backed by the JDK {@link HttpClient}
     */
    public static WebHttpFetcher jdk(final WebCrawlingConfig.HttpClientConfig httpClientConfig) {
        return new JdkWebHttpFetcher(httpClientConfig);
    }

    /**
     * Default {@link WebHttpFetcher} implementation backed by the JDK
     * {@link HttpClient}.
     */
    static final class JdkWebHttpFetcher implements WebHttpFetcher, AutoCloseable {

        private final HttpClient httpClient;
        private final WebCrawlingConfig.HttpClientConfig httpClientConfig;

        JdkWebHttpFetcher(final WebCrawlingConfig.HttpClientConfig httpClientConfig) {
            this(HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .executor(Executors.newVirtualThreadPerTaskExecutor()) // todo: we should close this at the end of the life-cycle
                    .build(), httpClientConfig);
        }

        JdkWebHttpFetcher(final HttpClient httpClient, final WebCrawlingConfig.HttpClientConfig httpClientConfig) {
            this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
            this.httpClientConfig = Objects.requireNonNull(httpClientConfig, "httpClientConfig must not be null");
        }

        /**
         * Fetches the given URI using an HTTP GET request.
         *
         * @param uri target URI to fetch
         * @return transport-agnostic HTTP response representation
         */
        @Override
        public WebHttpResponse fetch(final URI uri) {
            Objects.requireNonNull(uri, "uri must not be null");

            final HttpRequest request = HttpRequest.newBuilder(uri)
                    .GET()
                    .timeout(DEFAULT_REQUEST_TIMEOUT)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .build();

            try {
                final HttpResponse<String> response = this.httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                final String contentType = extractContentType(response.headers().map());

                return new WebHttpResponse(
                        response.uri(),
                        response.statusCode(),
                        response.body(),
                        contentType,
                        response.headers().map()
                );
            } catch (final InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("HTTP fetch interrupted for URI: " + uri, exception);
            } catch (final IOException exception) {
                throw new IllegalStateException("HTTP fetch failed for URI: " + uri, exception);
            }
        }

        private String extractContentType(final Map<String, List<String>> headers) {
            if (headers == null || headers.isEmpty()) {
                return "";
            }

            return headers.entrySet().stream()
                    .filter(entry -> "content-type".equalsIgnoreCase(entry.getKey()))
                    .map(Map.Entry::getValue)
                    .filter(values -> values != null && !values.isEmpty())
                    .map(values -> values.get(0))
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse("");
        }

        @Override
        public void close() throws Exception {
            if (Objects.isNull(httpClient)) {

                this.httpClient.close();
            }
        }
    }
}
