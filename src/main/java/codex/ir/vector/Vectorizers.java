package codex.ir.vector;

import codex.ir.corpus.vector.Vocabulary;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class Vectorizers {

    private Vectorizers () {}

    public static Vectorizer<SparseDocumentVector> sparse (final Vocabulary vocabulary) {
        return new SparseVectorizer(vocabulary);
    }

    /**
     * Builds sparse document vectors from weighted terms using a shared vocabulary.
     * @author jsanca & elo
     */
    private static final class SparseVectorizer implements Vectorizer<SparseDocumentVector> {

        private final Vocabulary vocabulary;

        public SparseVectorizer(final Vocabulary vocabulary) {
            this.vocabulary = Objects.requireNonNull(vocabulary, "vocabulary must not be null");
        }

        /**
         * Vectorizes a document from a map of normalized term -> weight.
         *
         * @param termWeights normalized term weights
         * @return sparse vector with precomputed norm
         */
        @Override
        public SparseDocumentVector vectorize(
                final Map<String, Double> termWeights
        ) {
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
}
