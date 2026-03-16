package codex.ir.indexer;

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
     * term → postings
     *
     * where each posting records the document identifier together with
     * term-level metadata such as frequency and positions.
     *
     * This is appropriate for experimentation, testing, and small corpora.
     */
    static class InMemoryInvertedIndex implements InvertedIndex {

        private final Map<String, Posting.Accumulator> termAccumulatorMap = new HashMap<>();

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
            final Map<String, List<Posting>> snapshot = new HashMap<>();

            for (final Map.Entry<String, Posting.Accumulator> entry : termAccumulatorMap.entrySet()) {
                snapshot.put(entry.getKey(), List.copyOf(entry.getValue().values()));
            }

            return Collections.unmodifiableMap(snapshot);
        }
    }
}
