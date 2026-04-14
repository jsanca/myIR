package codex.ir.vector;

/**
 * Similarity strategy for vector comparisons.
 *
 * @param <T> vector type
 * @jsanca & elo
 */
public interface Similarity<T> {

    /**
     * Computes similarity between two vectors.
     *
     * @param left left vector
     * @param right right vector
     * @return similarity score
     */
    double score(T left, T right);
}