
package codex.ir.util;

/**
 * Public utility methods for reusable term-weighting math.
 *
 * <p>This class intentionally exposes the weighting formulas used by both
 * ranking and vector construction so they can later be extracted or moved
 * without changing the calling code.</p>
 * @author jsanca & elo
 */
public final class TermWeightingUtils {

    private TermWeightingUtils() {
    }

    /**
     * Computes classical inverse document frequency using {@code log(N / df)}.
     *
     * @param corpusSize total number of documents in the corpus
     * @param documentFrequency number of documents containing the term
     * @return classical IDF value, or {@code 0.0d} when the inputs do not allow
     * a meaningful result
     */
    public static double classicIdf(final int corpusSize, final int documentFrequency) {
        if (corpusSize <= 0 || documentFrequency <= 0) {
            return 0.0d;
        }

        return Math.log((double) corpusSize / documentFrequency);
    }

    /**
     * Computes BM25 inverse document frequency using the smoothed formula
     * commonly written as {@code log(1 + ((N - df + 0.5) / (df + 0.5)))}.
     *
     * @param corpusSize total number of documents in the corpus
     * @param documentFrequency number of documents containing the term
     * @return BM25 IDF value, or {@code 0.0d} when the inputs do not allow a
     * meaningful result
     */
    public static double bm25Idf(final int corpusSize, final int documentFrequency) {
        if (corpusSize <= 0 || documentFrequency <= 0) {
            return 0.0d;
        }

        return Math.log(1.0d + ((double) (corpusSize - documentFrequency + 0.5d)
                / (documentFrequency + 0.5d)));
    }

    /**
     * Computes sublinear term frequency using {@code 1 + log(tf)}.
     *
     * @param termFrequency term frequency in a document
     * @return sublinear TF value, or {@code 0.0d} when {@code termFrequency <= 0}
     */
    public static double sublinearTf(final int termFrequency) {
        if (termFrequency <= 0) {
            return 0.0d;
        }

        return 1.0d + Math.log(termFrequency);
    }
}
