package codex.ir.ingestion.crawler;

import codex.ir.canonicalizer.UriCanonicalizer;
import codex.ir.canonicalizer.UriCanonicalizers;
import codex.ir.ingestion.WebCrawlingConfig;
import codex.ir.ingestion.crawler.classifier.UrlClassifier;
import codex.ir.ingestion.crawler.classifier.UrlClassifiers;
import codex.ir.ingestion.crawler.classifier.UrlFilter;
import codex.ir.ingestion.crawler.classifier.UrlFilters;
import codex.ir.ingestion.crawler.WebPageFetcher;
import codex.ir.ingestion.crawler.fetcher.WebHttpFetcher;
import codex.ir.ingestion.crawler.fetcher.WebHttpFetchers;
import codex.ir.ingestion.crawler.internal.sitemap.SitemapSiteTraversalStrategy;
import codex.ir.ingestion.crawler.internal.traversal.SeededWebPageTraversal;
import codex.ir.ingestion.crawler.internal.traversal.SiteTraversalStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Factory and container for {@link WebPageSourceStrategy} implementations.
 *
 * <p>This class keeps the public ingestion API focused on intent while hiding
 * the concrete traversal mechanics behind strategy implementations.</p>
 * @author jsanca & elo
 */
public final class WebPageSourceStrategies {

    private static final Logger LOGGER = LoggerFactory.getLogger(WebPageSourceStrategies.class);
    private static final boolean NO_EXPAND_LINKS = false;

    private WebPageSourceStrategies() {
    }

    /**
     * Creates a default site traversal strategy using the static HTML fetch path.
     *
     * @param config crawling configuration
     * @param rootUris initial seed URIs
     * @return traversal strategy
     */
    public static WebPageSourceStrategy siteTraversal(
            final WebCrawlingConfig config,
            final URI... rootUris
    ) {
        return siteTraversal(
                config,
                UriCanonicalizers.defaultWeb(),
                VisitedUriRegistries.inMemory(),
                WebPageFetcherRegistries.simple(config.httpClientConfig()),
                rootUris
        );
    }

    /**
     * Creates a default site traversal strategy using the provided fetcher registry.
     *
     * @param config crawling configuration
     * @param webPageFetcherRegistry fetcher registry used to reuse fetcher instances
     * @param rootUris initial seed URIs
     * @return traversal strategy
     */
    public static WebPageSourceStrategy siteTraversal(
            final WebCrawlingConfig config,
            final UriCanonicalizer uriCanonicalizer,
            final WebPageFetcherRegistry webPageFetcherRegistry,
            final URI... rootUris
    ) {
        return siteTraversal(
                config,
                uriCanonicalizer,
                VisitedUriRegistries.inMemory(),
                webPageFetcherRegistry,
                rootUris
        );
    }

    /**
     * Creates a default site traversal strategy using the static HTML fetch path
     * and the provided visited URI registry.
     *
     * @param config crawling configuration
     * @param visitedUriRegistry visited URI registry shared or scoped by the caller
     * @param rootUris initial seed URIs
     * @return traversal strategy
     */
    public static WebPageSourceStrategy siteTraversal(
            final WebCrawlingConfig config,
            final VisitedUriRegistry visitedUriRegistry,
            final URI... rootUris
    ) {
        return siteTraversal(
                config,
                UriCanonicalizers.defaultWeb(),
                visitedUriRegistry,
                WebPageFetcherRegistries.simple(config.httpClientConfig()),
                rootUris
        );
    }

    /**
     * Creates a default site traversal strategy using the provided visited URI registry
     * and fetcher registry.
     *
     * @param config crawling configuration
     * @param visitedUriRegistry visited URI registry shared or scoped by the caller
     * @param webPageFetcherRegistry fetcher registry used to reuse fetcher instances
     * @param rootUris initial seed URIs
     * @return traversal strategy
     */
    public static WebPageSourceStrategy siteTraversal(
            final WebCrawlingConfig config,
            final UriCanonicalizer uriCanonicalizer,
            final VisitedUriRegistry visitedUriRegistry,
            final WebPageFetcherRegistry webPageFetcherRegistry,
            final URI... rootUris
    ) {
        Objects.requireNonNull(config, "config must not be null");
        Objects.requireNonNull(uriCanonicalizer, "uriCanonicalizer must not be null");
        Objects.requireNonNull(visitedUriRegistry, "visitedUriRegistry must not be null");
        Objects.requireNonNull(webPageFetcherRegistry, "webPageFetcherRegistry must not be null");
        Objects.requireNonNull(rootUris, "rootUris must not be null");

        final Set<URI> seeds = Arrays.stream(rootUris)
                .filter(Objects::nonNull)
                .map(uriCanonicalizer::canonicalize)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());

        if (seeds.isEmpty()) {
            throw new IllegalArgumentException("At least one root URI is required");
        }

        return new SiteTraversalStrategy(
                config,
                uriCanonicalizer,
                seeds,
                webPageFetcherRegistry::staticHtml,
                visitedUriRegistry,
                true
        );
    }

    /**
     * Creates a sitemap-based traversal strategy using the static HTML fetch path.
     *
     * <p>Uses the default {@link UrlClassifiers#defaultWeb()} classifier and an
     * accept-all filter (backward-compatible — all discovered URLs are fetched).</p>
     *
     * @param config crawling configuration
     * @param rootUri the base URL for sitemap discovery
     * @return sitemap traversal strategy
     */
    public static WebPageSourceStrategy sitemapTraversal(
            final WebCrawlingConfig config,
            final URI rootUri
    ) {
        return sitemapTraversal(
                config,
                rootUri,
                UrlClassifiers.wordpressWooCommerceDefaultWeb(),
                UrlFilters.acceptAll()
        );
    }

    /**
     * Creates a sitemap-based traversal strategy with URL classification
     * and filtering.
     *
     * @param config crawling configuration
     * @param rootUri the base URL for sitemap discovery
     * @param urlClassifier classifies discovered URLs into types
     * @param urlFilter decides which classified URLs to include
     * @return sitemap traversal strategy
     */
    public static WebPageSourceStrategy sitemapTraversal(
            final WebCrawlingConfig config,
            final URI rootUri,
            final UrlClassifier urlClassifier,
            final UrlFilter urlFilter
    ) {
        return sitemapTraversal(
                config,
                rootUri,
                UriCanonicalizers.defaultWeb(),
                VisitedUriRegistries.inMemory(),
                WebPageFetcherRegistries.simple(config.httpClientConfig())::staticHtml,
                WebHttpFetchers.jdk(config.httpClientConfig()),
                urlClassifier,
                urlFilter
        );
    }

    /**
     * Creates a sitemap-based traversal strategy with full control over dependencies.
     *
     * @param config crawling configuration
     * @param rootUri the base URL for sitemap discovery
     * @param uriCanonicalizer URI canonicalization strategy
     * @param visitedUriRegistry visited URI tracker
     * @param fetcherFactory produces WebPageFetcher instances for page fetching
     * @param httpFetcher low-level HTTP fetcher for sitemap and robots.txt requests
     * @return sitemap traversal strategy
     */
    public static WebPageSourceStrategy sitemapTraversal(
            final WebCrawlingConfig config,
            final URI rootUri,
            final UriCanonicalizer uriCanonicalizer,
            final VisitedUriRegistry visitedUriRegistry,
            final Supplier<WebPageFetcher> fetcherFactory,
            final WebHttpFetcher httpFetcher
    ) {
        return sitemapTraversal(
                config, rootUri, uriCanonicalizer, visitedUriRegistry,
                fetcherFactory, httpFetcher, null, null
        );
    }

    /**
     * Creates a sitemap-based traversal strategy with full control including
     * URL classification and filtering.
     *
     * @param config crawling configuration
     * @param rootUri the base URL for sitemap discovery
     * @param uriCanonicalizer URI canonicalization strategy
     * @param visitedUriRegistry visited URI tracker
     * @param fetcherFactory produces WebPageFetcher instances for page fetching
     * @param httpFetcher low-level HTTP fetcher for sitemap and robots.txt requests
     * @param urlClassifier classifies discovered URLs into types, or null to skip
     * @param urlFilter decides which classified URLs to include, or null to skip
     * @return sitemap traversal strategy
     */
    public static WebPageSourceStrategy sitemapTraversal(
            final WebCrawlingConfig config,
            final URI rootUri,
            final UriCanonicalizer uriCanonicalizer,
            final VisitedUriRegistry visitedUriRegistry,
            final Supplier<WebPageFetcher> fetcherFactory,
            final WebHttpFetcher httpFetcher,
            final UrlClassifier urlClassifier,
            final UrlFilter urlFilter
    ) {
        Objects.requireNonNull(config, "config must not be null");
        Objects.requireNonNull(rootUri, "rootUri must not be null");
        Objects.requireNonNull(uriCanonicalizer, "uriCanonicalizer must not be null");
        Objects.requireNonNull(visitedUriRegistry, "visitedUriRegistry must not be null");
        Objects.requireNonNull(fetcherFactory, "fetcherFactory must not be null");
        Objects.requireNonNull(httpFetcher, "httpFetcher must not be null");

        final SeededWebPageTraversal seededTraversal = (seedUris, consumer) -> {
            final SiteTraversalStrategy pageFetcher = new SiteTraversalStrategy(
                    config,
                    uriCanonicalizer,
                    seedUris,
                    fetcherFactory,
                    visitedUriRegistry,
                    NO_EXPAND_LINKS
            );
            try (pageFetcher) {
                pageFetcher.readInto(consumer);
            }
        };

        return new SitemapSiteTraversalStrategy(
                config,
                rootUri,
                uriCanonicalizer,
                httpFetcher,
                seededTraversal,
                urlClassifier,
                urlFilter
        );
    }

}
