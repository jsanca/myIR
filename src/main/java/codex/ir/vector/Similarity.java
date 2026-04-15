package codex.ir.vector;

/**
 * Similarity strategy for vector comparisons.
 *
 * @param <T> vector type
 * @jsanca & elo
 */
public interface Similarity<T> {

    /**
     * Computes the full similarity result between two vectors.
     *
     * @param left left vector
     * @param right right vector
     * @return similarity computation result including score and contributing matches
     */
    SimilarityResult similarity(T left, T right);

    /**
     * Computes the similarity score between two vectors.
     * <p>
     * This is a convenience method derived from {@link #similarity(Object, Object)}.
     *
     * @param left left vector
     * @param right right vector
     * @return similarity score
     */
    default double score(final T left, final T right) {
        return this.similarity(left, right).score();
    }
}