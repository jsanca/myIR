package codex.ir.vector;

import java.util.Map;
import java.util.Objects;
import java.util.ArrayList;
import java.util.List;

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
         * Computes cosine similarity between two sparse document vectors and
         * returns the contributing shared dimensions.
         *
         * @param left left vector
         * @param right right vector
         * @return cosine similarity result, or a zero-score result when one of
         * the vectors has zero norm
         */
        @Override
        public SimilarityResult similarity(final SparseDocumentVector left, final SparseDocumentVector right) {
            Objects.requireNonNull(left, "left vector must not be null");
            Objects.requireNonNull(right, "right vector must not be null");

            if (left.metadata().norm() == 0.0d || right.metadata().norm() == 0.0d) {
                return new SimilarityResult(0.0d, List.of());
            }

            final boolean isLeftGreater = left.dimensions() > right.dimensions();
            final Map<Integer, Double> smaller = isLeftGreater ? right.weights() : left.weights();
            final Map<Integer, Double> larger = isLeftGreater ? left.weights() : right.weights();
            final List<SimilarityMatch> matches = new ArrayList<>();

            double dotProduct = 0.0d;
            for (final Map.Entry<Integer, Double> entry : smaller.entrySet()) {
                final Integer dimension = entry.getKey();
                final Double other = larger.get(dimension);
                if (other == null) {
                    continue;
                }

                final double leftWeight = left.weights().getOrDefault(dimension, 0.0d);
                final double rightWeight = right.weights().getOrDefault(dimension, 0.0d);
                final double contribution = leftWeight * rightWeight;

                dotProduct += contribution;
                matches.add(new SimilarityMatch(dimension, leftWeight, rightWeight, contribution));
            }

            final double score = dotProduct / (left.metadata().norm() * right.metadata().norm());
            return new SimilarityResult(score, List.copyOf(matches));
        }
    }
}
