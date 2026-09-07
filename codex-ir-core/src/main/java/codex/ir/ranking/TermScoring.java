package codex.ir.ranking;

import java.util.Optional;

/**
 * The result of evaluating one {@code (term, posting)} pair by a {@link Ranker}.
 *
 * <p>A {@code TermScoring} carries:
 * <ul>
 *   <li>{@link #base()} — the ranker's contribution before any field-aware boost.</li>
 *   <li>{@link #fieldBoost()} — present only when a non-neutral boost was applied.</li>
 *   <li>{@link #contribution()} — the final value used in scoring:
 *       {@code base × boostFactor} when a {@link FieldBoost} is present, otherwise {@code base}.</li>
 * </ul>
 *
 * <p>Each {@link Ranker} implementation returns a concrete subtype that exposes only
 * the statistics meaningful to that ranker (e.g., {@link BinaryTermScoring},
 * {@link TfIdfTermScoring}, {@link Bm25TermScoring}). This interface is intentionally
 * unsealed so future experimental rankers can provide their own implementation without
 * modifying existing code.
 */
public interface TermScoring {

    /** The normalized query term this scoring was produced for. */
    String term();

    /** The ranker's contribution before any field-aware boost is applied. */
    double base();

    /**
     * The field-boost breakdown, present only when the ranking context carried explicit
     * field weights and the posting had field-frequency data.
     *
     * <p>{@code Optional.empty()} means the score is purely the {@link #base()} —
     * no boost was computed or applied.
     */
    Optional<FieldBoost> fieldBoost();

    /**
     * The final contribution used when accumulating a document score.
     *
     * <p>Equal to {@code base × fieldBoost.boostFactor()} when a {@link FieldBoost}
     * is present, or equal to {@code base} otherwise.
     */
    double contribution();
}
