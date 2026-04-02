package codex.ir.corpus;

import codex.ir.Document;
import codex.ir.concurrent.Debouncer;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Utility factory class for creating Corpus implementations.
 *
 * This class provides convenient factory methods to obtain
 * common Corpus implementations without exposing their
 * concrete classes to the public API.
 *
 * Example usage:
 * Corpus corpus = Corpuses.inMemory();
 */
public final class Corpora {

    private Corpora() {}

    /**
     * Creates a new in-memory implementation of a Corpus.
     *
     * The returned corpus stores documents in a simple
     * in-memory HashMap using the document id as the key.
     * This implementation is suitable for experiments,
     * testing, and small datasets.
     *
     * @return a new in-memory Corpus instance
     */
    public static Corpus inMemory() {

        return new InMemoryCorpus();
    }

    /**
     * Simple in-memory Corpus implementation.
     *
     * Documents are stored in a HashMap keyed by their
     * identifier. This implementation is not thread-safe
     * and is intended for experimentation, prototyping,
     * or small-scale indexing scenarios.
     */
    static class InMemoryCorpus implements Corpus {

        private static final String STATISTICS_REFRESH_KEY = "corpus-statistics-refresh";
        private static final long STATISTICS_REFRESH_DEBOUNCE_MILLIS = 250L;
        private final Map<String, Document> documentMap = new HashMap<>();
        private final AtomicReference<CorpusStatistics> statisticsCache = new AtomicReference<>();
        private final Debouncer debouncer = new Debouncer();

        /**
         * Adds a document to the corpus.
         *
         * If a document with the same id already exists,
         * it will be replaced.
         */
        @Override
        public void add(final Document document) {

            Objects.requireNonNull(document);
            documentMap.put(document.id(), document);
            statisticsCache.set(null);
            debouncer.debounce(
                    STATISTICS_REFRESH_KEY,
                    this::refreshStatistics,
                    STATISTICS_REFRESH_DEBOUNCE_MILLIS,
                    java.util.concurrent.TimeUnit.MILLISECONDS
            );

        }

        /**
         * Retrieves a document by its identifier.
         *
         * @param documentId the document identifier
         * @return an Optional containing the document if present
         */
        @Override
        public Optional<Document> get(final String documentId) {
            Objects.requireNonNull(documentId);

            return Optional.ofNullable(documentMap.get(documentId));
        }


        /**
         * Checks whether a document with the given id exists
         * in the corpus.
         *
         * @param documentId the document identifier
         * @return true if the document exists
         */
        @Override
        public boolean contains(final String documentId) {
            Objects.requireNonNull(documentId);
            return documentMap.containsKey(documentId);
        }


        /**
         * Returns an iterable view of all documents stored
         * in the corpus.
         *
         * This method can be used for streaming the entire
         * collection when performing experiments, statistics
         * gathering, or corpus-wide processing.
         */
        @Override
        public Iterable<Document> documents() {

            return this.documentMap.values();
        }


        /**
         * Returns the number of documents stored in the corpus.
         *
         * @return the corpus size
         */
        @Override
        public int size() {

            return this.documentMap.size();
        }

        /**
         * Returns aggregated statistics describing the current corpus state.
         *
         * The statistics projection is cached and recomputed lazily only after
         * the corpus changes. This keeps repeated ranking operations efficient
         * while preserving a clean responsibility boundary: the corpus owns its
         * own derived metadata.
         *
         * @return immutable aggregated corpus statistics for the current corpus
         */
        @Override
        public CorpusStatistics statistics() {

            final CorpusStatistics cached = this.statisticsCache.get();
            if (cached != null) {
                return cached;
            }

            final CorpusStatistics computed = CorpusStatistics.from(this);
            this.statisticsCache.compareAndSet(null, computed);
            return this.statisticsCache.get();
        }

        private void refreshStatistics() {
            this.statisticsCache.set(CorpusStatistics.from(this));
        }
    }
}
