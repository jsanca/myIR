package codex.ir.indexer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents a posting entry in the inverted index.
 *
 * <p>A posting connects a term to a specific document and records:
 * <ul>
 *   <li>the document identifier</li>
 *   <li>the whole-document term frequency</li>
 *   <li>the whole-document positions where the term appears</li>
 *   <li>per-field term frequencies (IR-3) — empty for raw-content documents</li>
 * </ul>
 *
 * <p>{@code termFrequency} and {@code positions} always represent the
 * whole-document aggregated view and are unchanged by field analysis.
 * {@code fieldFrequencies} is populated only when the document was indexed
 * via structured fields; it maps each field name to the number of times the
 * term appeared in that field.
 *
 * @author jsanca
 */
public record Posting(
        String documentId,
        int termFrequency,
        List<Integer> positions,
        Map<String, Integer> fieldFrequencies
) {
    public Posting {
        positions = positions == null ? List.of() : List.copyOf(positions);
        fieldFrequencies = fieldFrequencies == null ? Map.of() : Map.copyOf(fieldFrequencies);
    }

    /**
     * Accumulator used during indexing.
     *
     * <p>Internally stores postings by document identifier so lookups
     * are O(1) when accumulating occurrences for a term. Per-field frequencies
     * are tracked separately and merged into {@link Posting#fieldFrequencies()}
     * when the accumulator is read.
     */
    public static class Accumulator {

        private final Map<String, List<Integer>> positionsByDocument = new ConcurrentHashMap<>();
        private final Map<String, Map<String, Integer>> fieldFreqsByDocument = new ConcurrentHashMap<>();

        /**
         * Adds a whole-document occurrence for a document at the given position.
         */
        public void add(final String documentId, final int position) {
            positionsByDocument
                    .computeIfAbsent(documentId, ignored -> Collections.synchronizedList(new ArrayList<>()))
                    .add(position);
        }

        /**
         * Records one occurrence of this term in the named field of the document.
         * Must be called after {@link #add} has been called at least once for the same document.
         */
        public void addFieldOccurrence(final String documentId, final String fieldName) {
            fieldFreqsByDocument
                    .computeIfAbsent(documentId, ignored -> new ConcurrentHashMap<>())
                    .merge(fieldName, 1, Integer::sum);
        }

        /**
         * Returns the postings accumulated for this term.
         */
        public Collection<Posting> values() {
            final List<Posting> postings = new ArrayList<>();

            for (final Map.Entry<String, List<Integer>> entry : positionsByDocument.entrySet()) {
                final String docId = entry.getKey();
                final List<Integer> sourcePositions = entry.getValue();
                final List<Integer> positions;
                synchronized (sourcePositions) {
                    positions = List.copyOf(sourcePositions);
                }
                final Map<String, Integer> fieldFreqs =
                        Map.copyOf(fieldFreqsByDocument.getOrDefault(docId, Map.of()));
                postings.add(new Posting(docId, positions.size(), positions, fieldFreqs));
            }

            return List.copyOf(postings);
        }

        /**
         * Returns the posting for a specific document if present.
         */
        public Posting get(final String documentId) {
            final List<Integer> positions = positionsByDocument.get(documentId);
            if (positions == null) {
                return null;
            }

            final List<Integer> immutablePositions;
            synchronized (positions) {
                immutablePositions = List.copyOf(positions);
            }
            final Map<String, Integer> fieldFreqs =
                    Map.copyOf(fieldFreqsByDocument.getOrDefault(documentId, Map.of()));
            return new Posting(documentId, immutablePositions.size(), immutablePositions, fieldFreqs);
        }
    }
}
