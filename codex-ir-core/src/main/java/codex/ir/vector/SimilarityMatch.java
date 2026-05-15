package codex.ir.vector;

/**
 * Represents a shared vector dimension that contributed to a similarity score.
 *
 * @param dimension the shared vector dimension
 * @param leftWeight the weight of the dimension in the left vector
 * @param rightWeight the weight of the dimension in the right vector
 * @param contribution the contribution of this dimension to the dot product
 * @author jsanca & elo
 */
public record SimilarityMatch(
        int dimension,
        double leftWeight,
        double rightWeight,
        double contribution
) {
}
