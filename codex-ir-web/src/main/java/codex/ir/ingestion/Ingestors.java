package codex.ir.ingestion;

import codex.ir.indexer.Indexer;

import java.util.Objects;

/**
 * Factory and container for {@link DocumentIngestionService} implementations.
 *
 * <p>This class centralizes creation of ingestion services in the same style as
 * other project factories such as tokenizers, normalizers, or rankers. The
 * default implementation coordinates a {@link DocumentSource}, a
 * {@link DocumentMapper}, and an {@link Indexer} without coupling the indexing
 * engine to any particular ingestion mechanism.</p>
 * @author jsanca & elo
 */
public final class Ingestors {

    private Ingestors() {
    }

    /**
     * Creates the default ingestion service implementation.
     *
     * @param <T> type of elements produced by the source and consumed by the mapper
     * @return default ingestion service
     */
    public static <T> DocumentIngestionService<T> simple() {
        return new SimpleDocumentIngestionService<>();
    }

    /**
     * Default sequential implementation of {@link DocumentIngestionService}.
     *
     * @param <T> type of elements produced by the source and consumed by the mapper
     */
    static final class SimpleDocumentIngestionService<T> implements DocumentIngestionService<T> {

        /**
         * Executes the ingestion pipeline sequentially.
         *
         * <p>Each element produced by the source is mapped into a document and
         * immediately indexed. This implementation is intentionally simple and
         * serves as the baseline ingestion strategy for the project.</p>
         *
         * @param source the source of input elements
         * @param mapper transforms input elements into documents
         * @param indexer receives and indexes the resulting documents
         */
        @Override
        public void ingest(
                final DocumentSource<T> source,
                final DocumentMapper<T> mapper,
                final Indexer indexer
        ) {
            Objects.requireNonNull(source, "source must not be null");
            Objects.requireNonNull(mapper, "mapper must not be null");
            Objects.requireNonNull(indexer, "indexer must not be null");

            source.readInto(item -> indexer.index(mapper.map(item)));
        }
    }
}
