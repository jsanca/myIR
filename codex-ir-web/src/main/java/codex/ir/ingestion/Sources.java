package codex.ir.ingestion;

import codex.ir.ingestion.crawler.WebPageSourceStrategies;
import codex.ir.ingestion.crawler.WebPageSourceStrategy;

import java.io.Closeable;
import java.net.URI;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

public final class Sources {

    private Sources() {}

    public static DocumentSource<WebPage> webPageFromSiteMap(final URI... rootUris) {
        return webPage(defaultConfig(), rootUris);
    }

    public static DocumentSource<WebPage> webPageFromSiteMap(final WebCrawlingConfig config,
                                                             final URI... rootUris) {
        return webPage(config, rootUris);
    }

    public static DocumentSource<WebPage> webPage(final WebPageSourceStrategy webPageSourceStrategy) {

        return new WebPageDocumentSource(webPageSourceStrategy);
    }

    public static DocumentSource<WebPage> webPage(final URI... rootUris) {
        return webPage(defaultConfig(), rootUris);
    }

    public static DocumentSource<WebPage> webPage(final WebCrawlingConfig config,
                                                  final URI... rootUris) {

        Objects.requireNonNull(rootUris, "rootUris must not be null");
        if (rootUris.length == 0) {
            throw new IllegalArgumentException("At least one root URI is required");
        }
        Objects.requireNonNull(config, "config must not be null");

        return new WebPageDocumentSource(WebPageSourceStrategies.siteTraversal(
                config,
                rootUris
            ));
    }

    private static WebCrawlingConfig defaultConfig() {
        return WebCrawlingConfig.defaultConfig();
    }

    /**
     * Default {@link DocumentSource} implementation for web page ingestion.
     *
     * <p>This record acts as a thin adapter between the high-level ingestion API
     * and the underlying crawling strategies. It delegates the actual traversal
     * and page fetching to a {@link codex.ir.ingestion.crawler.WebPageSourceStrategy}
     * created based on the provided configuration and root URIs.</p>
     *
     * <p>The source is immutable and encapsulates all the information required
     * to perform a crawl session.</p>
     *
     * @param config crawling configuration controlling traversal behavior
     * @param rootUris initial seed URIs for the traversal
     */
    private record WebPageDocumentSource(WebPageSourceStrategy webPageSourceStrategy) implements DocumentSource<WebPage>, AutoCloseable {

        /**
         * Starts the crawling process and emits each discovered {@link WebPage}
         * into the provided consumer.
         *
         * <p>This method delegates the traversal logic to the configured
         * {@link codex.ir.ingestion.crawler.WebPageSourceStrategy}, keeping this
         * class focused on adapting the ingestion API to the crawling layer.</p>
         *
         * @param consumer consumer that receives each fetched web page
         */
        @Override
            public void readInto(final Consumer<WebPage> consumer) {

                Objects.requireNonNull(consumer, "consumer must not be null");
                this.webPageSourceStrategy.readInto(consumer);
            }

        @Override
        public void close() throws Exception {

            if (webPageSourceStrategy != null &&
                    webPageSourceStrategy instanceof AutoCloseable closeable) {

                closeable.close();
            }
        }
    }
}
