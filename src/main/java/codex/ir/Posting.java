package codex.ir;

import java.util.ArrayList;
import java.util.List;

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
 *     Posting(doc1, tf=3, positions=[1, 8, 22]),
 *     Posting(doc4, tf=1, positions=[5])
 * ]
 *
 * Storing positions allows advanced search features such as:
 * - phrase queries
 * - proximity search
 * - highlighting
 * @author jsanca
 */
public class Posting {

    private final String documentId;
    private int termFrequency;
    private final List<Integer> positions;

    /**
     * Creates a new posting entry for a document.
     *
     * @param documentId the identifier of the document containing the term
     */
    public Posting(final String documentId) {
        this.documentId = documentId;
        this.termFrequency = 0;
        this.positions = new ArrayList<>();
    }

    /**
     * Records a new occurrence of the term at the given position.
     *
     * @param position token position inside the document
     */
    public void addOccurrence(final int position) {
        this.termFrequency++;
        this.positions.add(position);
    }

    /**
     * @return the document identifier
     */
    public String getDocumentId() {
        return documentId;
    }

    /**
     * @return number of times the term appears in the document
     */
    public int getTermFrequency() {
        return termFrequency;
    }

    /**
     * Returns an immutable copy of the positions list.
     *
     * @return list of token positions
     */
    public List<Integer> getPositions() {
        return List.copyOf(positions);
    }
}
