package codex.ir.vector;

import codex.ir.corpus.vector.Vocabulary;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Builds sparse document vectors from weighted terms using a shared vocabulary.
 * @author jsanca & elo
 */
public final class SparseVectorizer {

    private final Vocabulary vocabulary;

    public SparseVectorizer(final Vocabulary vocabulary) {
        this.vocabulary = Objects.requireNonNull(vocabulary, "vocabulary must not be null");
    }

    /**
     * Vectorizes a document from a map of normalized term -> weight.
     *
     * @param documentId document identifier
     * @param termWeights normalized term weights
     * @return sparse vector with precomputed norm
     */
    public SparseDocumentVector vectorize(
            final String documentId,
            final Map<String, Double> termWeights
    ) {
        Objects.requireNonNull(documentId, "documentId must not be null");
        Objects.requireNonNull(termWeights, "termWeights must not be null");

        final Map<Integer, Double> sparseWeights = new HashMap<>();
        double squaredNorm = 0.0d;

        for (final Map.Entry<String, Double> entry : termWeights.entrySet()) {
            final String term = Objects.requireNonNull(entry.getKey(), "term must not be null");
            final Double weight = Objects.requireNonNull(entry.getValue(), "weight must not be null");

            if (weight == 0.0d) {
                continue;
            }

            final int termId = vocabulary.getOrCreateTermId(term);
            sparseWeights.put(termId, weight);
            squaredNorm += weight * weight;
        }

        final double norm = Math.sqrt(squaredNorm);
        return new SparseDocumentVector(documentId, sparseWeights,
                new SparseVectorMetadata(norm));
    }
}