package codex.ir.vector;

import java.util.List;

/**
 * Represents the outcome of a similarity computation between two vectors.
 *
 * @param score the final similarity score
 * @param matches the shared dimensions that contributed to the score
 * @author jsanca & elo
 */
public record SimilarityResult(
        double score,
        List<SimilarityMatch> matches
) {
}
