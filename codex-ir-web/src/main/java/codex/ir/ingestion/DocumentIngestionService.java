package codex.ir.ingestion;

import codex.ir.indexer.Indexer;


/**
 * Orchestrates the ingestion pipeline from a {@link DocumentSource} into an {@link Indexer}.
 *
 * <p>This service coordinates three main responsibilities:</p>
 * <ul>
 *     <li>reading elements from a {@link DocumentSource}</li>
 *     <li>mapping each element into a {@link codex.ir.Document} using a {@link DocumentMapper}</li>
 *     <li>indexing the resulting documents through the {@link Indexer}</li>
 * </ul>
 *
 * <p>The ingestion model is intentionally decoupled, allowing different sources
 * (e.g., file systems, crawlers, APIs) and different mapping strategies to be
 * combined without changing the indexing logic.</p>
 *
 * @param <T> type of elements produced by the source and consumed by the mapper
 */
public interface DocumentIngestionService<T> {

    /**
     * Executes the ingestion pipeline.
     *
     * <p>All elements produced by the given {@link DocumentSource} are passed
     * through the provided {@link DocumentMapper}, and the resulting documents
     * are forwarded to the {@link Indexer}.</p>
     *
     * <p>Implementations may choose to process elements sequentially or in
     * parallel, depending on performance requirements.</p>
     *
     * @param source the source of input elements
     * @param mapper transforms input elements into documents
     * @param indexer receives and indexes the resulting documents
     */
    void ingest(DocumentSource<T> source, DocumentMapper<T> mapper, Indexer indexer);
}
