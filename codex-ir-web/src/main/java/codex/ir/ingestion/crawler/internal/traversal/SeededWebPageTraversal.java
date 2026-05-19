package codex.ir.ingestion.crawler.internal.traversal;

import codex.ir.ingestion.WebPage;

import java.net.URI;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Small delegate responsible for processing a set of seed page URIs into
 * emitted {@link WebPage} instances.
 *
 * <p>This abstraction decouples the sitemap discovery logic from the
 * concrete traversal mechanism. The delegate is free to use BFS traversal
 * with or without link expansion, a simple sequential fetcher, or any
 * other strategy that satisfies the contract.</p>
 */
@FunctionalInterface
public interface SeededWebPageTraversal {

    /**
     * Processes the given seed URIs and emits fetched pages into the
     * provided consumer.
     *
     * @param seedUris canonical page URIs discovered from sitemaps
     * @param consumer receiver of fetched {@link WebPage} instances
     */
    void traverse(Set<URI> seedUris, Consumer<WebPage> consumer);
}
