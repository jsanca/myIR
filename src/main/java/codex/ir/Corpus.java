package codex.ir;

import java.util.Optional;

/**
 * Represents the collection of documents that belong to the information
 * retrieval system.
 *
 * The Corpus is responsible for storing and providing access to documents
 * by their identifier. It acts as the authoritative source of documents
 * referenced by the InvertedIndex.
 *
 * Typical responsibilities include:
 * - storing documents when they are indexed
 * - resolving a document id to its corresponding Document
 * - providing iteration over the entire collection for experiments,
 *   statistics, or analysis tasks
 */
public interface Corpus {

    /**
     * Adds a document to the corpus.
     *
     * Implementations may choose to replace an existing document with the
     * same identifier or reject duplicates depending on their policy.
     *
     * @param document the document to add to the corpus
     */
    void add(Document document);

    /**
     * Retrieves a document by its identifier.
     *
     * @param id the document identifier
     * @return an Optional containing the document if present
     */
    Optional<Document> get(String id);

    /**
     * Checks whether the corpus contains a document with the given id.
     *
     * @param id the document identifier
     * @return true if the document exists in the corpus
     */
    boolean contains(String id);

    /**
     * Returns an iterable view of all documents in the corpus.
     *
     * This method is useful for streaming or iterating over the full
     * document collection in experiments, statistics gathering,
     * summarization, or other corpus-wide operations.
     *
     * @return an iterable over all documents in the corpus
     */
    Iterable<Document> documents();

    /**
     * Returns the number of documents stored in the corpus.
     *
     * @return the total number of documents
     */
    int size();
}
