package codex.ir;

import java.util.*;

public final class InvertedIndexes {


    public static InvertedIndex inMemory() {

        return new InMemoryInvertedIndex();
    }

    /**
     * Default in‑memory implementation of the inverted index.
     *
     * This implementation stores the entire index in a HashMap structure:
     *
     * term → set(documentId)
     *
     * This is appropriate for experimentation, testing, and small corpora.
     */
    static class InMemoryInvertedIndex implements InvertedIndex {

        private final Map<String, Set<String>> index = new HashMap<>();

        @Override
        public void add(final String term, final String documentId) {
            if (term == null || term.isBlank() || documentId == null || documentId.isBlank()) {
                return;
            }

            index.computeIfAbsent(term, ignored -> new TreeSet<>()).add(documentId);
        }

        @Override
        public Set<String> search(final String term) {
            if (term == null || term.isBlank()) {
                return Set.of();
            }

            return index.getOrDefault(term, Collections.emptySet());
        }

        @Override
        public Map<String, Set<String>> asMap() {
            return Collections.unmodifiableMap(index);
        }
    }
}
