package codex.ir.corpus;

import codex.ir.Document;
import codex.ir.concurrent.Debouncer;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
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

    public enum CorpusStatisticsRefreshMode {
        DEBOUNCED,
        EAGER
    }

    public static Corpus inMemory() {

        return inMemory(CorpusStatisticsRefreshMode.DEBOUNCED);
    }

    public static Corpus inMemory(final CorpusStatisticsRefreshMode refreshMode) {

        return new InMemoryCorpus(refreshMode, 250L);
    }

    /**
     * Simple in-memory Corpus implementation.
     *
     * Documents are stored in a concurrent in-memory map keyed by their
     * identifier. The corpus also maintains incremental aggregate statistics
     * to support efficient ranking operations.
     *
     * The statistics projection is exposed as an immutable snapshot that is
     * refreshed in a debounced way after corpus mutations. This keeps
     * repeated ranking operations efficient while avoiding unnecessary
     * snapshot rebuilds during ingestion bursts.
     */
    static class InMemoryCorpus implements Corpus {

        private static final String STATISTICS_REFRESH_KEY = "corpus-statistics-refresh";
        private final CorpusStatisticsRefreshMode refreshMode;
        private final long statisticsRefreshDebounceMillis;
        private final Map<String, Document> documentMap = new ConcurrentHashMap<>();
        private final Debouncer debouncer = new Debouncer();
        private final AtomicReference<Long> statisticsSnapshotVersion = new AtomicReference<>(0L);
        private final AtomicReference<CorpusStatistics> statisticsCache = new AtomicReference<>(
                CorpusStatistics.from(0L, 0, 0L, 0)
        );
        private final AtomicReference<Long> totalDocumentLength = new AtomicReference<>(0L);
        private final AtomicReference<Integer> documentsWithLength = new AtomicReference<>(0);
        private final Object statisticsMutationLock = new Object();

        private InMemoryCorpus(final CorpusStatisticsRefreshMode refreshMode,
                               final long statisticsRefreshDebounceMillis) {
            this.refreshMode = Objects.requireNonNull(refreshMode);
            this.statisticsRefreshDebounceMillis = statisticsRefreshDebounceMillis;
        }

        /**
         * Adds a document to the corpus.
         *
         * If a document with the same id already exists,
         * it will be replaced.
         */
        @Override
        public void add(final Document document) {

            Objects.requireNonNull(document);

            synchronized (statisticsMutationLock) {
                final Document previousDocument = documentMap.put(document.id(), document);
                adjustStatisticsForReplacement(previousDocument, document);
                refreshStatisticsSnapshot();
            }
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
         * The statistics projection is exposed as an immutable snapshot that is
         * refreshed in a debounced way after corpus mutations. This keeps
         * repeated ranking operations efficient while avoiding unnecessary
         * snapshot rebuilds during ingestion bursts.
         *
         * @return immutable aggregated corpus statistics for the current corpus
         */
        @Override
        public CorpusStatistics statistics() {

            return this.statisticsCache.get();
        }

        private void adjustStatisticsForReplacement(final Document previousDocument, final Document newDocument) {
            if (previousDocument != null) {
                final Integer previousLength = extractDocumentLength(previousDocument);
                if (previousLength != null) {
                    totalDocumentLength.updateAndGet(current -> current - previousLength.longValue());
                    documentsWithLength.updateAndGet(current -> current - 1);
                }
            }

            final Integer newLength = extractDocumentLength(newDocument);
            if (newLength != null) {
                totalDocumentLength.updateAndGet(current -> current + newLength.longValue());
                documentsWithLength.updateAndGet(current -> current + 1);
            }
        }

        private Integer extractDocumentLength(final Document document) {
            if (document == null || document.metadata() == null) {
                return null;
            }
            return document.metadata().length();
        }

        private void refreshStatisticsSnapshot() {
            if (this.refreshMode == CorpusStatisticsRefreshMode.EAGER) {
                updateStatisticsSnapshot();
                return;
            }
            scheduleStatisticsSnapshotRefresh();
        }

        private void scheduleStatisticsSnapshotRefresh() {
            this.debouncer.debounce(
                    STATISTICS_REFRESH_KEY,
                    () -> {
                        synchronized (statisticsMutationLock) {
                            updateStatisticsSnapshot();
                        }
                    },
                    this.statisticsRefreshDebounceMillis,
                    TimeUnit.MILLISECONDS
            );
        }

        private void updateStatisticsSnapshot() {
            final long nextVersion = this.statisticsSnapshotVersion.updateAndGet(current -> current + 1L);
            this.statisticsCache.set(buildCurrentStatisticsSnapshot(nextVersion));
        }

        private CorpusStatistics buildCurrentStatisticsSnapshot(final long version) {
            return CorpusStatistics.from(
                    version,
                    this.documentMap.size(),
                    this.totalDocumentLength.get(),
                    this.documentsWithLength.get()
            );
        }
    }
}
