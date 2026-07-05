package codex.ir.indexer;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class InvertedIndexes {

    private InvertedIndexes() {}



    public static InvertedIndex inMemory() {

        return new InMemoryInvertedIndex();
    }

    /**
     * Default in‑memory implementation of the inverted index.
     *
     * This implementation stores the entire index in a HashMap structure:
     *
     * term → postings
     *
     * where each posting records the document identifier together with
     * term-level metadata such as frequency and positions.
     *
     * This is appropriate for experimentation, testing, and small corpora.
     */
    static class InMemoryInvertedIndex implements InvertedIndex {

        private final Map<String, Posting.Accumulator> termAccumulatorMap = new ConcurrentHashMap<>();

        @Override
        public void add(final String term, final String documentId, final int position) {
            if (term == null || term.isBlank() || documentId == null || documentId.isBlank()) {
                return;
            }

            final Posting.Accumulator accumulator =
                    termAccumulatorMap.computeIfAbsent(term, ignored -> new Posting.Accumulator());

            accumulator.add(documentId, position);
        }

        @Override
        public List<Posting> getPostings(final String term) {
            if (term == null || term.isBlank()) {
                return List.of();
            }

            final Posting.Accumulator accumulator = termAccumulatorMap.get(term);
            if (accumulator == null) {
                return List.of();
            }

            return List.copyOf(accumulator.values());
        }

        @Override
        public Map<String, List<Posting>> asMap() {
            final Map<String, List<Posting>> result = new HashMap<>();

            for (final Map.Entry<String, Posting.Accumulator> entry : termAccumulatorMap.entrySet()) {
                result.put(entry.getKey(), List.copyOf(entry.getValue().values()));
            }

            return Collections.unmodifiableMap(result);
        }

        @Override
        public IndexSnapshot snapshot() {
            return new InMemoryIndexSnapshot(asMap());
        }
    }

    private static final class InMemoryIndexSnapshot implements IndexSnapshot {

        private final Map<String, List<Posting>> index;

        private InMemoryIndexSnapshot(final Map<String, List<Posting>> index) {
            this.index = Objects.requireNonNull(index);
        }

        @Override
        public List<Posting> getPostings(final String term) {
            if (term == null || term.isBlank()) {
                return List.of();
            }
            return index.getOrDefault(term, List.of());
        }

        @Override
        public Map<String, List<Posting>> asMap() {
            return index;
        }
    }
}
