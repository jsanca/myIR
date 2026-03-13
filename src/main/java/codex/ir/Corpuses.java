
package codex.ir;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

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
public final class Corpuses {

    private Corpuses() {}

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

        private final Map<String, Document> documentMap = new HashMap<>();

        @Override
        /**
         * Adds a document to the corpus.
         *
         * If a document with the same id already exists,
         * it will be replaced.
         */
        public void add(final Document document) {

            Objects.requireNonNull(document);
            documentMap.put(document.id(), document);

        }

        @Override
        /**
         * Retrieves a document by its identifier.
         *
         * @param documentId the document identifier
         * @return an Optional containing the document if present
         */
        public Optional<Document> get(final String documentId) {
            Objects.requireNonNull(documentId);

            return Optional.ofNullable(documentMap.get(documentId));
        }

        @Override
        /**
         * Checks whether a document with the given id exists
         * in the corpus.
         *
         * @param documentId the document identifier
         * @return true if the document exists
         */
        public boolean contains(final String documentId) {
            Objects.requireNonNull(documentId);
            return documentMap.containsKey(documentId);
        }

        @Override
        /**
         * Returns an iterable view of all documents stored
         * in the corpus.
         *
         * This method can be used for streaming the entire
         * collection when performing experiments, statistics
         * gathering, or corpus-wide processing.
         */
        public Iterable<Document> documents() {

            return this.documentMap.values();
        }

        @Override
        /**
         * Returns the number of documents stored in the corpus.
         *
         * @return the corpus size
         */
        public int size() {

            return this.documentMap.size();
        }
    }
}
