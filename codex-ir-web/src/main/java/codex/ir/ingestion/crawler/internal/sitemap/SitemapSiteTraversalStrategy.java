package codex.ir.ingestion.crawler.internal.sitemap;

import codex.ir.canonicalizer.UriCanonicalizer;
import codex.ir.ingestion.WebCrawlingConfig;
import codex.ir.ingestion.WebPage;
import codex.ir.ingestion.crawler.internal.traversal.SeededWebPageTraversal;
import codex.ir.ingestion.crawler.WebPageSourceStrategy;
import codex.ir.ingestion.crawler.classifier.ClassifiedUrl;
import codex.ir.ingestion.crawler.classifier.UrlClassifier;
import codex.ir.ingestion.crawler.filter.UrlFilter;
import codex.ir.ingestion.crawler.fetcher.WebHttpFetcher;
import codex.ir.ingestion.crawler.fetcher.WebHttpResponse;
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

/**
 * Produces {@link WebPage} instances by discovering URLs from XML
 * sitemaps and delegating page fetching to an injected
 * {@link SeededWebPageTraversal} delegate.
 *
 * <p>This strategy supports:</p>
 * <ul>
 *   <li>robots.txt sitemap discovery</li>
 *   <li>known sitemap path fallback ({@code /sitemap.xml}, etc.)</li>
 *   <li>{@code <sitemapindex>} recursive parsing</li>
 *   <li>{@code <urlset>} URL extraction</li>
 *   <li>URL canonicalization and deduplication</li>
 * </ul>
 *
 * <p>The strategy is focused only on sitemap discovery and URL collection.
 * It does not know how page URIs are fetched or emitted — that
 * responsibility belongs to the injected delegate.</p>
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
    private final WebHttpFetcher httpFetcher;
    private final SeededWebPageTraversal seededTraversal;
    private final UrlClassifier urlClassifier;
    private final UrlFilter urlFilter;
    private final SitemapParser sitemapParser;
    private final RobotsParser robotsParser;

    public SitemapSiteTraversalStrategy(
            final WebCrawlingConfig config,
            final URI rootUri,
            final UriCanonicalizer uriCanonicalizer,
            final WebHttpFetcher httpFetcher,
            final SeededWebPageTraversal seededTraversal
    ) {
        this(config, rootUri, uriCanonicalizer, httpFetcher, seededTraversal, null, null);
    }

    public SitemapSiteTraversalStrategy(
            final WebCrawlingConfig config,
            final URI rootUri,
            final UriCanonicalizer uriCanonicalizer,
            final WebHttpFetcher httpFetcher,
            final SeededWebPageTraversal seededTraversal,
            final UrlClassifier urlClassifier,
            final UrlFilter urlFilter
    ) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.rootUri = Objects.requireNonNull(rootUri, "rootUri must not be null");
        this.uriCanonicalizer = Objects.requireNonNull(uriCanonicalizer, "uriCanonicalizer must not be null");
        this.httpFetcher = Objects.requireNonNull(httpFetcher, "httpFetcher must not be null");
        this.seededTraversal = Objects.requireNonNull(seededTraversal, "seededTraversal must not be null");
        this.urlClassifier = urlClassifier;
        this.urlFilter = urlFilter;
        this.sitemapParser = new SitemapParser();
        this.robotsParser = new RobotsParser(httpFetcher);
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

        if (pageUris.isEmpty()) {
            LOGGER.debug("No page URIs collected from sitemaps for {}", rootUri);
            return;
        }

        final Set<URI> acceptedUris = classifyAndFilter(pageUris);
        LOGGER.debug("{} page URI(s) accepted after classification and filtering for {}",
                acceptedUris.size(), rootUri);

        if (acceptedUris.isEmpty()) {
            LOGGER.debug("No page URIs accepted after filtering for {}", rootUri);
            return;
        }

        seededTraversal.traverse(acceptedUris, consumer);
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

    private Set<URI> classifyAndFilter(final Set<URI> pageUris) {
        if (urlClassifier == null || urlFilter == null) {
            return pageUris;
        }

        final Set<URI> accepted = new LinkedHashSet<>();
        for (final URI pageUri : pageUris) {
            final ClassifiedUrl classified = urlClassifier.classify(pageUri);
            if (urlFilter.accepts(classified)) {
                accepted.add(pageUri);
            } else {
                LOGGER.debug("Filtering out URI {} classified as {}", pageUri, classified.type());
            }
        }
        return accepted;
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

    private boolean isDisallowedPath(final URI uri) {
        final String path = uri.getPath() == null ? "" : uri.getPath();
        return config.disallowedPaths().stream().anyMatch(path::startsWith);
    }
}
