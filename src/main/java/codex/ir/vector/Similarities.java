package codex.ir.vector;

import java.util.Map;
import java.util.Objects;

/**
 * Factory methods for vector similarity strategies.
 */
public final class Similarities {


    /**
     * Creates a cosine similarity strategy for sparse document vectors.
     *
     * @return sparse cosine similarity implementation
     */
    public static Similarity<SparseDocumentVector> sparseCosine() {

        return new SparseCosineSimilarity();
    }
    /**
     * Cosine similarity for sparse vectors.
     *
     * <p>This implementation iterates the smaller sparse map and probes the
     * larger one to compute the dot product only across shared active
     * dimensions.</p>
     */
    private static class SparseCosineSimilarity implements Similarity<SparseDocumentVector> {

        /**
         * Computes cosine similarity between two sparse document vectors.
         *
         * @param left left vector
         * @param right right vector
         * @return cosine similarity score, or {@code 0.0d} when one of the
         * vectors has zero norm
         */
        @Override
        public double score(final SparseDocumentVector left, final SparseDocumentVector right) {
            Objects.requireNonNull(left, "left vector must not be null");
            Objects.requireNonNull(right, "right vector must not be null");

            if (left.metadata().norm() == 0.0d || right.metadata().norm() == 0.0d) {
                return 0.0d;
            }

            // Assume left is the smaller vector and right is the larger one.
            final boolean isLeftGreater = left.dimensions() > right.dimensions();
            final Map<Integer, Double> smaller = isLeftGreater? right.weights(): left.weights();
            final Map<Integer, Double> larger  = isLeftGreater? left.weights(): right.weights();

            double dotProduct = 0.0d;
            for (final Map.Entry<Integer, Double> entry : smaller.entrySet()) {
                final Double other = larger.get(entry.getKey());
                if (other != null) {
                    dotProduct += entry.getValue() * other;
                }
            }

            return dotProduct / (left.metadata().norm() * right.metadata().norm());
        }
    }
}
