package codex.ir.ingestion.crawler.internal.traversal;

import codex.ir.canonicalizer.UriCanonicalizer;
import codex.ir.concurrent.VTConfig;
import codex.ir.concurrent.VTExecutor;
import codex.ir.concurrent.VTExecutors;
import codex.ir.ingestion.WebCrawlingConfig;
import codex.ir.ingestion.WebPage;
import codex.ir.ingestion.crawler.VisitedUriRegistry;
import codex.ir.ingestion.crawler.WebPageFetcher;
import codex.ir.ingestion.crawler.WebPageSourceStrategy;
import codex.ir.web.util.HttpUtil;
import codex.ir.web.util.UriUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Traverses a site breadth-first using a shared frontier and a visited
 * registry.
 *
 * <p>This strategy is responsible only for traversal concerns:
 * frontier management, depth control, visited tracking, link filtering,
 * backpressure-aware task submission, and emitting fetched pages into the
 * provided consumer.</p>
 *
 * <p>When {@code expandLinks} is {@code true} (the default for full BFS
 * crawls), discovered links from each fetched page are enqueued back into
 * the frontier for further traversal. When {@code false} (used for
 * sitemap-based traversal), only the seed URIs are fetched; discovered
 * links are discarded.</p>
 */
public final class SiteTraversalStrategy implements WebPageSourceStrategy, AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(SiteTraversalStrategy.class);
    private static final long FRONTIER_POLL_TIMEOUT_MILLIS = 250L;

    private final WebCrawlingConfig config;
    private final UriCanonicalizer uriCanonicalizer;
    private final Set<URI> seeds;
    private final Supplier<WebPageFetcher> fetcherFactory;
    private final VisitedUriRegistry visitedUriRegistry;
    private final boolean expandLinks;
    private final BlockingQueue<TraversalNode> frontier = new LinkedBlockingQueue<>();
    private final AtomicInteger inFlightTasks = new AtomicInteger();
    private final AtomicInteger emittedPages = new AtomicInteger();
    private final VTExecutor executor;

    public SiteTraversalStrategy(
            final WebCrawlingConfig config,
            final UriCanonicalizer uriCanonicalizer,
            final Set<URI> seeds,
            final Supplier<WebPageFetcher> fetcherFactory,
            final VisitedUriRegistry visitedUriRegistry,
            final boolean expandLinks
    ) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.uriCanonicalizer = Objects.requireNonNull(uriCanonicalizer, "uriCanonicalizer must not be null");
        this.seeds = Objects.requireNonNull(seeds, "seeds must not be null");
        this.fetcherFactory = Objects.requireNonNull(fetcherFactory, "fetcherFactory must not be null");
        this.visitedUriRegistry = Objects.requireNonNull(visitedUriRegistry, "visitedUriRegistry must not be null");
        this.expandLinks = expandLinks;
        this.executor = VTExecutors.createVirtualThreadExecutor(new VTConfig(config.maxConcurrentRequests()));
    }

    @Override
    public void readInto(final Consumer<WebPage> consumer) {
        Objects.requireNonNull(consumer, "consumer must not be null");

        seedFrontier();

        while (true) {
            if (this.emittedPages.get() >= this.config.maxPages()) {
                LOGGER.debug("Stopping traversal because maxPages={} was reached", this.config.maxPages());
                break;
            }

            final TraversalNode nextNode = pollNextNode();
            if (nextNode == null) {
                if (this.inFlightTasks.get() == 0 && this.frontier.isEmpty()) {
                    LOGGER.debug("Traversal finished: no pending nodes and no in-flight tasks");
                    break;
                }
                continue;
            }

            if (!shouldVisit(nextNode)) {
                continue;
            }

            this.inFlightTasks.incrementAndGet();
            this.executor.execute(() -> processNode(nextNode, consumer));
        }
    }

    private void seedFrontier() {
        for (final URI seed : this.seeds) {
            this.frontier.offer(new TraversalNode(seed, 0, seed));
        }
        LOGGER.debug("Seeded traversal frontier with {} URI(s)", this.seeds.size());
    }

    private TraversalNode pollNextNode() {
        try {
            return this.frontier.poll(FRONTIER_POLL_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (final InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Traversal interrupted while waiting for frontier nodes", exception);
        }
    }

    private boolean shouldVisit(final TraversalNode node) {
        if (node.depth() > this.config.maxDepth()) {
            return false;
        }

        final URI canonicalUri = this.uriCanonicalizer.canonicalize(node.uri());

        if (!HttpUtil.isHttpUri(canonicalUri)) {
            return false;
        }

        if (!UriUtil.isAllowedByDomainRules(canonicalUri, node.rootUri(), this.config)) {
            return false;
        }

        if (isDisallowedPath(canonicalUri)) {
            return false;
        }

        return this.visitedUriRegistry.markVisited(canonicalUri);
    }

    private void processNode(final TraversalNode node, final Consumer<WebPage> consumer) {

        try {
            if (this.config.delayMillisBetweenRequests() > 0) {
                Thread.sleep(this.config.delayMillisBetweenRequests());
            }

            final Consumer<Set<URI>> linkSubscriber = getLinkSubscriber(node);

            final WebPageFetcher fetcher = this.fetcherFactory.get();
            final URI canonicalUri = this.uriCanonicalizer.canonicalize(node.uri());
            fetcher.fetch(canonicalUri, linkSubscriber).ifPresent(page -> emitPage(page, consumer));
        } catch (final InterruptedException exception) {
            Thread.currentThread().interrupt();
            LOGGER.warn("Traversal task interrupted for URI {}", node.uri(), exception);
        } catch (final RuntimeException exception) {
            LOGGER.warn("Traversal task failed for URI {}", node.uri(), exception);
        } finally {
            this.inFlightTasks.decrementAndGet();
        }
    }

    private Consumer<Set<URI>> getLinkSubscriber(final TraversalNode node) {
        return this.expandLinks
                ? links -> enqueueDiscoveredLinks(node, links)
                : links -> { // ignored
        };
    }

    private void emitPage(final WebPage page, final Consumer<WebPage> consumer) {
        while (true) {
            final int current = this.emittedPages.get();
            if (current >= this.config.maxPages()) {
                return;
            }
            if (this.emittedPages.compareAndSet(current, current + 1)) {
                consumer.accept(page);
                return;
            }
        }
    }

    private void enqueueDiscoveredLinks(final TraversalNode parentNode, final Set<URI> discoveredLinks) {
        if (discoveredLinks == null || discoveredLinks.isEmpty()) {
            return;
        }

        final int nextDepth = parentNode.depth() + 1;
        if (nextDepth > this.config.maxDepth()) {
            return;
        }

        for (final URI discoveredLink : discoveredLinks) {
            if (discoveredLink == null) {
                continue;
            }

            final URI canonicalDiscoveredLink = this.uriCanonicalizer.canonicalize(discoveredLink);
            if (!HttpUtil.isHttpUri(canonicalDiscoveredLink)
                    || !UriUtil.isAllowedByDomainRules(canonicalDiscoveredLink, parentNode.rootUri(), this.config)
                    || isDisallowedPath(canonicalDiscoveredLink)
                    || this.visitedUriRegistry.isVisited(canonicalDiscoveredLink)) {
                continue;
            }

            this.frontier.offer(new TraversalNode(canonicalDiscoveredLink, nextDepth, parentNode.rootUri()));
        }
    }

    private boolean isDisallowedPath(final URI uri) {
        final String path = uri.getPath() == null ? "" : uri.getPath();
        return this.config.disallowedPaths().stream().anyMatch(path::startsWith);
    }

    @Override
    public void close() {
        if (null != executor) {
            executor.close();
        }
    }

    private record TraversalNode(URI uri, int depth, URI rootUri) {
    }
}
