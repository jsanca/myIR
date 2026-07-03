/**
 * Document ranking strategies.
 * <p>
 * {@link codex.ir.ranking.Ranker} defines the scoring contract.
 * {@link codex.ir.ranking.Rankers} provides implementations:
 * </p>
 * <ul>
 *   <li><b>Binary</b> — binary presence scoring</li>
 *   <li><b>TF-IDF</b> — classic term frequency × inverse document frequency</li>
 *   <li><b>BM25</b> — probabilistic ranking with configurable k₁ and b parameters</li>
 * </ul>
 */
package codex.ir.ranking;
