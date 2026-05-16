package codex.ir.ingestion.crawler.sitemap;

import codex.ir.canonicalizer.UriCanonicalizer;
import codex.ir.ingestion.WebCrawlingConfig;
import codex.ir.ingestion.WebPage;
import codex.ir.ingestion.crawler.WebPageFetcher;
import codex.ir.ingestion.crawler.WebPageSourceStrategy;
import codex.ir.ingestion.crawler.fetcher.WebHttpFetcher;
import codex.ir.ingestion.crawler.fetcher.WebHttpResponse;
import codex.ir.ingestion.crawler.VisitedUriRegistry;
import codex.ir.web.util.HttpUtil;
import codex.ir.web.util.UriUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Produces {@link WebPage} instances by discovering URLs from XML
 * sitemaps.
 *
 * <p>This strategy replaces the old {@code SiteMapStrategy} stub with a real
 * implementation that supports:</p>
 * <ul>
 *   <li>robots.txt sitemap discovery</li>
 *   <li>known sitemap path fallback ({@code /sitemap.xml}, etc.)</li>
 *   <li>{@code <sitemapindex>} recursive parsing</li>
 *   <li>{@code <urlset>} URL extraction</li>
 *   <li>URL canonicalization and deduplication</li>
 * </ul>
 */
public final class SitemapSiteTraversalStrategy implements WebPageSourceStrategy {

    private static final Logger LOGGER = LoggerFactory.getLogger(SitemapSiteTraversalStrategy.class);

    private static final List<String> KNOWN_SITEMAP_PATHS = List.of(
            "/wp-sitemap.xml",
            "/sitemap_index.xml",
            "/sitemap.xml",
            "/product-sitemap.xml",
            "/wp-sitemap-posts-product-1.xml"
    );

    private final WebCrawlingConfig config;
    private final URI rootUri;
    private final UriCanonicalizer uriCanonicalizer;
    private final VisitedUriRegistry visitedUriRegistry;
    private final Supplier<WebPageFetcher> fetcherFactory;
    private final WebHttpFetcher httpFetcher;
    private final SitemapParser sitemapParser;
    private final RobotsParser robotsParser;
    private int emittedPages;

    public SitemapSiteTraversalStrategy(
            final WebCrawlingConfig config,
            final URI rootUri,
            final UriCanonicalizer uriCanonicalizer,
            final VisitedUriRegistry visitedUriRegistry,
            final Supplier<WebPageFetcher> fetcherFactory,
            final WebHttpFetcher httpFetcher
    ) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.rootUri = Objects.requireNonNull(rootUri, "rootUri must not be null");
        this.uriCanonicalizer = Objects.requireNonNull(uriCanonicalizer, "uriCanonicalizer must not be null");
        this.visitedUriRegistry = Objects.requireNonNull(visitedUriRegistry, "visitedUriRegistry must not be null");
        this.fetcherFactory = Objects.requireNonNull(fetcherFactory, "fetcherFactory must not be null");
        this.httpFetcher = Objects.requireNonNull(httpFetcher, "httpFetcher must not be null");
        this.sitemapParser = new SitemapParser();
        this.robotsParser = new RobotsParser(httpFetcher);
        this.emittedPages = 0;
    }

    @Override
    public void readInto(final Consumer<WebPage> consumer) {
        Objects.requireNonNull(consumer, "consumer must not be null");

        final Set<URI> sitemapUris = discoverSitemapUris();
        if (sitemapUris.isEmpty()) {
            LOGGER.debug("No sitemap URIs discovered for {}", rootUri);
            return;
        }

        final Set<URI> pageUris = collectPageUris(sitemapUris);
        LOGGER.debug("Collected {} unique page URI(s) from sitemaps for {}", pageUris.size(), rootUri);

        fetchAndEmitPages(pageUris, consumer);
    }

    private Set<URI> discoverSitemapUris() {
        final Set<URI> sitemapUris = new LinkedHashSet<>();

        final List<URI> robotsTxtUris = robotsParser.discoverSitemapUris(rootUri);
        sitemapUris.addAll(robotsTxtUris);

        if (sitemapUris.isEmpty()) {
            LOGGER.debug("No sitemap directives in robots.txt for {}. Trying known paths.", rootUri);
            for (final String knownPath : KNOWN_SITEMAP_PATHS) {
                final URI candidate = buildKnownPathUri(knownPath);
                if (candidate != null && HttpUtil.isHttpUri(candidate)) {
                    sitemapUris.add(candidate);
                }
            }
        }

        return sitemapUris;
    }

    private URI buildKnownPathUri(final String path) {
        try {
            return new URI(
                    rootUri.getScheme(),
                    rootUri.getUserInfo(),
                    rootUri.getHost(),
                    rootUri.getPort(),
                    path,
                    null,
                    null
            );
        } catch (final Exception exception) {
            LOGGER.debug("Could not build known sitemap path URI: {}", path, exception);
            return null;
        }
    }

    private Set<URI> collectPageUris(final Set<URI> sitemapUris) {
        final Set<URI> pageUris = new LinkedHashSet<>();
        final Set<URI> processedSitemaps = new LinkedHashSet<>();

        for (final URI sitemapUri : sitemapUris) {
            processSitemap(sitemapUri, pageUris, processedSitemaps, 0);
        }

        return pageUris;
    }

    private void processSitemap(
            final URI sitemapUri,
            final Set<URI> pageUris,
            final Set<URI> processedSitemaps,
            final int depth
    ) {
        final URI canonicalSitemapUri = uriCanonicalizer.canonicalize(sitemapUri);
        if (!processedSitemaps.add(canonicalSitemapUri)) {
            return;
        }

        final Optional<WebHttpResponse> response = fetchSitemapResponse(canonicalSitemapUri);
        if (response.isEmpty()) {
            return;
        }

        final SitemapEntries entries = sitemapParser.parse(response.get().body(), canonicalSitemapUri);
        if (entries.isEmpty()) {
            return;
        }

        for (final SitemapEntry.UrlEntry urlEntry : entries.urlEntries()) {
            final URI canonicalPageUri = uriCanonicalizer.canonicalize(urlEntry.loc());

            if (!HttpUtil.isHttpUri(canonicalPageUri)) {
                continue;
            }
            if (!UriUtil.isAllowedByDomainRules(canonicalPageUri, rootUri, config)) {
                continue;
            }
            if (isDisallowedPath(canonicalPageUri)) {
                continue;
            }

            pageUris.add(canonicalPageUri);
        }

        final int nextDepth = depth + 1;
        for (final SitemapEntry.SitemapRef ref : entries.sitemapRefs()) {
            if (nextDepth <= config.maxDepth()) {
                processSitemap(ref.loc(), pageUris, processedSitemaps, nextDepth);
            }
        }
    }

    private Optional<WebHttpResponse> fetchSitemapResponse(final URI sitemapUri) {
        try {
            final WebHttpResponse response = httpFetcher.fetch(sitemapUri);
            if (!response.isSuccessful()) {
                LOGGER.warn("Failed to fetch sitemap at {}: status={}", sitemapUri, response.statusCode());
                return Optional.empty();
            }
            return Optional.of(response);
        } catch (final Exception exception) {
            LOGGER.warn("Exception fetching sitemap at {}", sitemapUri, exception);
            return Optional.empty();
        }
    }

    private void fetchAndEmitPages(final Set<URI> pageUris, final Consumer<WebPage> consumer) {
        for (final URI pageUri : pageUris) {
            if (emittedPages >= config.maxPages()) {
                LOGGER.debug("Stopping page emission because maxPages={} was reached", config.maxPages());
                return;
            }

            try {
                if (config.delayMillisBetweenRequests() > 0) {
                    Thread.sleep(config.delayMillisBetweenRequests());
                }

                final WebPageFetcher fetcher = fetcherFactory.get();
                fetcher.fetch(pageUri, ignored -> {}).ifPresent(page -> {
                    if (emittedPages < config.maxPages()) {
                        emittedPages++;
                        consumer.accept(page);
                    }
                });
            } catch (final InterruptedException exception) {
                Thread.currentThread().interrupt();
                LOGGER.warn("Sitemap traversal interrupted for URI {}", pageUri, exception);
                return;
            } catch (final Exception exception) {
                LOGGER.warn("Failed to fetch page for URI {}", pageUri, exception);
            }
        }
    }

    private boolean isDisallowedPath(final URI uri) {
        final String path = uri.getPath() == null ? "" : uri.getPath();
        return config.disallowedPaths().stream().anyMatch(path::startsWith);
    }
}
