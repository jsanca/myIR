package codex.ir.vector;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Sparse vector representation for a document.
 *
 * <p>Dimensions are represented by term ids. Missing dimensions are implicitly zero.</p>
 * @author jsanca & elo
 */
public record SparseDocumentVector(
        String documentId,
        Map<Integer, Double> weights,
        SparseVectorMetadata metadata
) {

    public SparseDocumentVector {
        Objects.requireNonNull(documentId, "documentId must not be null");
        Objects.requireNonNull(weights, "weights must not be null");
        Objects.requireNonNull(metadata, "metadata must not be null");

        weights = Collections.unmodifiableMap(weights);
    }

    /**
     * Returns the weight for the given term id, or zero if absent.
     *
     * @param termId term identifier
     * @return weight or zero
     */
    public double weightOf(final int termId) {
        return weights.getOrDefault(termId, 0.0d);
    }

    /**
     * Returns the number of active dimensions in this sparse vector.
     *
     * @return active dimensions count
     */
    public int dimensions() {
        return weights.size();
    }
}