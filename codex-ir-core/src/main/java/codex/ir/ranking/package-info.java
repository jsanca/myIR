/**
 * Document ranking strategies, field-aware boosting, and score explanation.
 *
 * <h2>Ranking</h2>
 * <p>
 * {@link codex.ir.ranking.Ranker} defines the scoring contract. The two-argument
 * {@code score(term, posting)} method provides whole-document scoring; the three-argument
 * {@code score(term, posting, context)} default applies a frequency-weighted field boost
 * derived from {@link codex.ir.ranking.RankingContext} and {@link codex.ir.ranking.FieldWeights}.
 * {@link codex.ir.ranking.Rankers} provides implementations:
 * </p>
 * <ul>
 *   <li><b>Binary</b> — binary presence scoring</li>
 *   <li><b>TF-IDF</b> — classic term frequency × inverse document frequency</li>
 *   <li><b>BM25</b> — probabilistic ranking with configurable k₁ and b parameters</li>
 * </ul>
 * <p>
 * {@link codex.ir.ranking.FieldWeights} maps field names to boost multipliers.
 * {@link codex.ir.ranking.RankingContext} wraps {@code FieldWeights} and is passed
 * per search request. {@link codex.ir.ranking.RankingContext#neutral()} produces a context
 * that leaves scores identical to whole-document ranking, ensuring full backward compatibility.
 * </p>
 *
 * <h2>Score Explanation (IR-5)</h2>
 * <p>
 * {@link codex.ir.ranking.TermScoring} is an unsealed interface representing the result of
 * evaluating one {@code (term, posting)} pair. Concrete implementations expose only the
 * statistics meaningful to their ranker:
 * </p>
 * <ul>
 *   <li>{@link codex.ir.ranking.BinaryTermScoring} — term, base (0.0 or 1.0), contribution</li>
 *   <li>{@link codex.ir.ranking.TfIdfTermScoring} — term, tf, sublinearTf, idf, base, contribution</li>
 *   <li>{@link codex.ir.ranking.Bm25TermScoring} — term, tf, idf, document length, k1, b,
 *       normalization, base, contribution</li>
 * </ul>
 * <p>
 * {@link codex.ir.ranking.FieldBoost} is attached to a {@code TermScoring} when a non-neutral
 * {@code RankingContext} applied a frequency-weighted boost. It records per-field effective
 * weights (unknown fields → 1.0), the weighted sum, total frequency, and the boost factor.
 * </p>
 * <p>
 * {@link codex.ir.ranking.ScoreExplanation} aggregates the ordered list of {@code TermScoring}
 * contributions for a single {@code (query, document)} pair and the reconstructed total score.
 * </p>
 */
package codex.ir.ranking;
