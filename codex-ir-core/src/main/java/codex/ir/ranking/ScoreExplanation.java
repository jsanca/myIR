package codex.ir.ranking;

import java.util.List;
import java.util.Objects;

/**
 * A per-document score explanation returned by {@code ExplainableSearcher.explain(query, documentId)}.
 *
 * <p>Carries the original query, the target document identifier, the reconstructed total score,
 * and the ordered list of per-term contributions that produced it.
 *
 * <p>The {@link #score()} field reflects the sum of {@link TermScoring#contribution()} values
 * in the same sequential order used by the searcher's scoring loop. Score conservation —
 * {@code score ≈ Σ contributions} — is enforced by tests, not by this constructor, because
 * IEEE 754 addition is not associative and a constructor assertion could throw on valid data
 * produced by a different summation order.
 *
 * @param query         the original (pre-analysis) query string
 * @param documentId    identifier of the explained document
 * @param score         reconstructed total score (sum of all contributions)
 * @param contributions per-term scoring details in scoring-loop iteration order
 */
public record ScoreExplanation(
        String query,
        String documentId,
        double score,
        List<TermScoring> contributions
) {
    public ScoreExplanation {
        Objects.requireNonNull(query, "query cannot be null");
        Objects.requireNonNull(documentId, "documentId cannot be null");
        Objects.requireNonNull(contributions, "contributions cannot be null");
        contributions = List.copyOf(contributions);
    }
}
