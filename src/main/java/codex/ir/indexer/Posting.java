package codex.ir.indexer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a posting entry in the inverted index.
 *
 * A posting connects a term to a specific document and records:
 *
 * - the document identifier
 * - the term frequency within that document
 * - the positions where the term appears
 *
 * Example structure in the inverted index:
 *
 * term -> [
 *     Posting(doc1)  // occurrences recorded with addOccurrence(...)
 *     Posting(doc4)
 * ]
 *
 * After indexing, a posting instance could contain:
 *
 * documentId = "doc1"
 * termFrequency = 3
 * positions = [1, 8, 22]
 *
 * Storing positions allows advanced search features such as:
 * - phrase queries
 * - proximity search
 * - highlighting
 *
 * @author jsanca
 */
public record Posting(
        String documentId,
        int termFrequency,
        List<Integer> positions
) {
    public Posting {
        positions = positions == null ? List.of() : List.copyOf(positions);
    }
    /**
     * Accumulator used during indexing.
     *
     * Internally stores postings by document identifier so lookups
     * are O(1) when accumulating occurrences for a term.
     *
     * The indexer only interacts with this abstraction and does not
     * need to know whether the underlying structure is a map or list.
     */
    public static class Accumulator {

        private final Map<String, List<Integer>> positionsByDocument = new HashMap<>();

        /**
         * Adds a new occurrence for a document at the given position.
         */
        public void add(final String documentId, final int position) {
            positionsByDocument
                    .computeIfAbsent(documentId, ignored -> new ArrayList<>())
                    .add(position);
        }

        /**
         * Returns the postings accumulated for this term.
         */
        public Collection<Posting> values() {
            final List<Posting> postings = new ArrayList<>();

            for (final Map.Entry<String, List<Integer>> entry : positionsByDocument.entrySet()) {
                final List<Integer> positions = List.copyOf(entry.getValue());
                postings.add(new Posting(entry.getKey(), positions.size(), positions));
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

            final List<Integer> immutablePositions = List.copyOf(positions);
            return new Posting(documentId, immutablePositions.size(), immutablePositions);
        }
    }
}
