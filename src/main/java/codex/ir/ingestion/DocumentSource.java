package codex.ir.ingestion;

import codex.ir.Document;

import java.util.function.Consumer;

/**
 * Abstraction representing a source capable of supplying documents to the
 * indexing pipeline.
 *
 * <p>The source is intentionally agnostic about where the data comes from.
 * Implementations may read documents from the file system, a web crawler,
 * object storage such as S3, a database, or any other external system.</p>
 *
 * <p>This interface follows a push-style ingestion model: the source pushes
 * each produced item into the provided consumer. This keeps the indexing side
 * decoupled from discovery and loading concerns, and it leaves room for future
 * parallel or incremental ingestion strategies.</p>
 *
 * @param <T> type produced by the source; in many cases this will be
 *            {@link Document}, but the abstraction also allows earlier
 *            ingestion stages to produce intermediate representations
 *            before they are mapped into documents
 * @author jsanca & elo
 */
@FunctionalInterface
public interface DocumentSource<T> {
    /**
     * Pushes all items produced by this source into the given consumer.
     *
     * <p>Implementations control how items are discovered and emitted.
     * The consumer is responsible for deciding what to do with each produced
     * item, such as mapping, filtering, or indexing it.</p>
     *
     * @param consumer receiver of items produced by this source
     */
    void readInto(Consumer<T> consumer);
}
