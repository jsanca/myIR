
package codex.ir.vector;

import java.util.Map;

/**
 * Converts weighted terms into a vector representation.
 *
 * @param <T> concrete vector type produced by this vectorizer
 * @author jsanca & elo
 */
public interface Vectorizer<T> {

    /**
     * Builds a vector from term weights.
     *
     * @param termWeights map of normalized terms to their weights
     * @return vector representation built from the provided weights
     */
    T vectorize(Map<String, Double> termWeights);
}
