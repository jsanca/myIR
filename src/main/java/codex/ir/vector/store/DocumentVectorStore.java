package codex.ir.vector.store;

import codex.ir.vector.SparseDocumentVector;

import java.util.Collection;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Defines the contract for storing and retrieving sparse document vectors.
 * <p>
 * Implementations may keep vectors in memory, on disk, or in an external system,
 * but they must provide basic persistence and traversal operations for
 * {@link SparseDocumentVector} instances identified by document id.
 * @author jsanca & elo
 */
public interface DocumentVectorStore {
    /**
     * Stores or replaces the vector associated with its document id.
     *
     * @param vector the sparse document vector to store
     */
    void save(SparseDocumentVector vector);
    /**
     * Finds the vector associated with the given document id.
     *
     * @param documentId the identifier of the document
     * @return an {@link Optional} containing the matching vector when present,
     * otherwise an empty optional
     */
    Optional<SparseDocumentVector> findByDocumentId(String documentId);
    /**
     * Returns all stored document vectors.
     * <p>
     * Implementations may choose to return a snapshot or a live view depending
     * on their semantics.
     *
     * @return all stored sparse document vectors
     */
    Collection<SparseDocumentVector> findAll();
    /**
     * Checks whether a vector exists for the given document id.
     *
     * @param documentId the identifier of the document
     * @return {@code true} if a vector exists for the document id, otherwise {@code false}
     */
    boolean contains(String documentId);
    /**
     * Removes the vector associated with the given document id.
     *
     * @param documentId the identifier of the document to remove
     */
    void delete(String documentId);
    /**
     * Returns an {@link Iterable} over all stored document vectors.
     * <p>
     * This method is useful when callers want traversal semantics without
     * forcing materialization into a separate collection.
     *
     * @return an iterable view over all stored sparse document vectors
     */
    Iterable<SparseDocumentVector> iterateAll();
    /**
     * Returns a {@link Stream} over all stored document vectors.
     *
     * @return a stream of all stored sparse document vectors
     */
    Stream<SparseDocumentVector> streamAll();
    /**
     * Applies the given consumer to each stored document vector.
     *
     * @param consumer the consumer to apply to each vector
     */
    void forEach(Consumer<SparseDocumentVector> consumer);
}
