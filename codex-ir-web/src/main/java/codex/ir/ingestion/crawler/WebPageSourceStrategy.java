package codex.ir.ingestion.crawler;

import codex.ir.ingestion.DocumentSource;
import codex.ir.ingestion.WebPage;

import java.util.function.Consumer;

/**
 * Strategy responsible for producing {@link WebPage} instances for a
 * {@link DocumentSource} focused on web ingestion.
 *
 * <p>This abstraction keeps the high-level source agnostic to the concrete
 * mechanism used to obtain pages. Different implementations may use site
 * traversal, sitemap discovery, fixed URL lists, API-backed sources, or other
 * strategies, while still exposing the same {@code WebPage} output model.</p>
 *
 * <p>The strategy follows a push-style model: each produced {@link WebPage} is
 * emitted into the provided consumer.</p>
 * @author jsanca & elo
 */
public interface WebPageSourceStrategy {

    /**
     * Produces web pages and pushes them into the provided consumer.
     *
     * @param consumer receiver of produced web pages
     */
    void readInto(Consumer<WebPage> consumer);
}
