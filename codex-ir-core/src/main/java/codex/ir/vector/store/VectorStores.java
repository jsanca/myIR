package codex.ir.vector.store;

import codex.ir.vector.SparseDocumentVector;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Stream;


/**
 * {@link DocumentVectorStore} factory
 * Returns inMemory stores of the type specified.
 *
 * @author jsanca
 * @author jsanca & elo
 */
public final class VectorStores {

    public static DocumentVectorStore inMemory() {
        return new InMemoryDocumentVectorStore();
    }

    private static final class InMemoryDocumentVectorStore implements DocumentVectorStore {

        private final Map<String, SparseDocumentVector> sparseDocumentVectorMap = new ConcurrentHashMap<>();

        @Override
        public void save(final SparseDocumentVector vector) {

            sparseDocumentVectorMap.put(vector.documentId(), vector);
        }

        @Override
        public Optional<SparseDocumentVector> findByDocumentId(final String documentId) {

            return Optional.ofNullable(this.sparseDocumentVectorMap.get(documentId));
        }

        @Override
        public Collection<SparseDocumentVector> findAll() {
            return List.copyOf(this.sparseDocumentVectorMap.values());
        }

        @Override
        public Iterable<SparseDocumentVector> iterateAll() {
            return this.sparseDocumentVectorMap.values();
        }

        @Override
        public Stream<SparseDocumentVector> streamAll() {
            return this.sparseDocumentVectorMap.values().stream();
        }

        @Override
        public void forEach(final Consumer<SparseDocumentVector> consumer) {

            streamAll().forEach(consumer);
        }

        @Override
        public boolean contains(final String documentId) {
            return this.sparseDocumentVectorMap.containsKey(documentId);
        }

        @Override
        public void delete(final String documentId) {

            this.sparseDocumentVectorMap.remove(documentId);
        }
    }
}
