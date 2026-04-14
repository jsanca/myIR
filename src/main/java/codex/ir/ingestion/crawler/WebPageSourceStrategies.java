package codex.ir.ingestion.crawler;

import codex.ir.concurrent.VTConfig;
import codex.ir.concurrent.VTExecutor;
import codex.ir.concurrent.VTExecutors;
import codex.ir.ingestion.WebCrawlingConfig;
import codex.ir.ingestion.WebPage;
import codex.ir.util.HttpUtil;
import codex.ir.util.UriUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.net.URI;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Factory and container for {@link WebPageSourceStrategy} implementations.
 *
 * <p>This class keeps the public ingestion API focused on intent while hiding
 * the concrete traversal mechanics behind strategy implementations.</p>
 * @author jsanca & elo
 */
public final class WebPageSourceStrategies {

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
            final WebPageFetcherRegistry webPageFetcherRegistry,
            final URI... rootUris
    ) {
        return siteTraversal(
                config,
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
            final VisitedUriRegistry visitedUriRegistry,
            final WebPageFetcherRegistry webPageFetcherRegistry,
            final URI... rootUris
    ) {
        Objects.requireNonNull(config, "config must not be null");
        Objects.requireNonNull(visitedUriRegistry, "visitedUriRegistry must not be null");
        Objects.requireNonNull(webPageFetcherRegistry, "webPageFetcherRegistry must not be null");
        Objects.requireNonNull(rootUris, "rootUris must not be null");

        final Set<URI> seeds = Arrays.stream(rootUris)
                .filter(Objects::nonNull)
                .map(URI::normalize)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());

        if (seeds.isEmpty()) {
            throw new IllegalArgumentException("At least one root URI is required");
        }

        return new SiteTraversalStrategy(
                config,
                seeds,
                webPageFetcherRegistry::staticHtml,
                visitedUriRegistry
        );
    }

    /**
     * Factory for building a {@link WebPageFetcher} bound to a specific link subscriber.
     */
    @FunctionalInterface
    private interface WebPageFetcherFactory  {
        WebPageFetcher create();
    }

    /**
     * Traverses a site breadth-first using a shared frontier and a visited registry.
     *
     * <p>This strategy is responsible only for traversal concerns:
     * frontier management, depth control, visited tracking, link filtering,
     * backpressure-aware task submission, and emitting fetched pages into the
     * provided consumer.</p>
     */
    private static class SiteTraversalStrategy implements WebPageSourceStrategy, AutoCloseable {

        private static final Logger LOGGER = LoggerFactory.getLogger(SiteTraversalStrategy.class);
        private static final long FRONTIER_POLL_TIMEOUT_MILLIS = 250L;

        private final WebCrawlingConfig config;
        private final Set<URI> rootUris;
        private final WebPageFetcherFactory fetcherFactory;
        private final VisitedUriRegistry visitedUriRegistry;
        private final BlockingQueue<TraversalNode> frontier = new LinkedBlockingQueue<>();
        private final AtomicInteger inFlightTasks = new AtomicInteger();
        private final AtomicInteger emittedPages = new AtomicInteger();
        private final VTExecutor executor;

        private SiteTraversalStrategy(
                final WebCrawlingConfig config,
                final Set<URI> rootUris,
                final WebPageFetcherFactory fetcherFactory,
                final VisitedUriRegistry visitedUriRegistry
        ) {
            this.config = Objects.requireNonNull(config, "config must not be null");
            this.rootUris = Objects.requireNonNull(rootUris, "rootUris must not be null");
            this.fetcherFactory = Objects.requireNonNull(fetcherFactory, "fetcherFactory must not be null");
            this.visitedUriRegistry = Objects.requireNonNull(visitedUriRegistry, "visitedUriRegistry must not be null");
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
            for (final URI rootUri : this.rootUris) {
                this.frontier.offer(new TraversalNode(rootUri.normalize(), 0, rootUri.normalize()));
            }
            LOGGER.debug("Seeded traversal frontier with {} root URI(s)", this.rootUris.size());
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

            final URI normalizedUri = UriUtil.normalizeUri(node.uri());
            if (normalizedUri == null) {
                return false;
            }

            if (!HttpUtil.isHttpUri(normalizedUri)) {
                return false;
            }

            if (!UriUtil.isAllowedByDomainRules(normalizedUri, node.rootUri(), this.config)) {
                return false;
            }

            if (isDisallowedPath(normalizedUri)) {
                return false;
            }

            return this.visitedUriRegistry.markVisited(normalizedUri);
        }

        private void processNode(final TraversalNode node, final Consumer<WebPage> consumer) {

            WebPageFetcher fetcher = null;
            try {
                if (this.config.delayMillisBetweenRequests() > 0) {
                    Thread.sleep(this.config.delayMillisBetweenRequests());
                }

                final Consumer<Set<URI>> linkSubscriber = links -> enqueueDiscoveredLinks(node, links);
                fetcher = this.fetcherFactory.create();
                fetcher.fetch(node.uri(), linkSubscriber).ifPresent(page -> emitPage(page, consumer));
            } catch (final InterruptedException exception) {
                Thread.currentThread().interrupt();
                LOGGER.warn("Traversal task interrupted for URI {}", node.uri(), exception);
            } catch (final RuntimeException exception) {
                LOGGER.warn("Traversal task failed for URI {}", node.uri(), exception);
            } finally {
                this.inFlightTasks.decrementAndGet();
            }
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
                final URI normalizedDiscoveredLink = UriUtil.normalizeUri(discoveredLink);
                if (normalizedDiscoveredLink == null || !HttpUtil.isHttpUri(normalizedDiscoveredLink) ||
                        !UriUtil.isAllowedByDomainRules(normalizedDiscoveredLink, parentNode.rootUri(), this.config) || isDisallowedPath(normalizedDiscoveredLink) ||
                        this.visitedUriRegistry.isVisited(normalizedDiscoveredLink)) {
                    continue;
                }

                this.frontier.offer(new TraversalNode(normalizedDiscoveredLink, nextDepth, parentNode.rootUri()));
            }
        }

        private boolean isDisallowedPath(final URI uri) {

            final String path = uri.getPath() == null ? "" : uri.getPath();
            return this.config.disallowedPaths().stream().anyMatch(path::startsWith);
        }

        @Override
        public void close() throws Exception {

            if (null != executor) {
                executor.close();
            }
        }
    }

    private static class SiteMapStrategy implements WebPageSourceStrategy {

        @Override
        public void readInto(final Consumer<WebPage> consumer) {
            throw new UnsupportedOperationException("SiteMapStrategy is not implemented yet");
        }
    }

    /**
     * Small traversal unit tracked in the frontier.
     */
    private record TraversalNode(URI uri, int depth, URI rootUri) {
    }
}
