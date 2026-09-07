package codex.ir.ranking;

import java.util.Objects;
import java.util.Optional;

/**
 * {@link TermScoring} produced by the binary ranker.
 *
 * <p>Binary ranking treats any matching posting as a contribution of {@code 1.0}
 * before field-aware boost; a missing posting contributes {@code 0.0}.
 * No TF or IDF statistics are meaningful for binary ranking and are not exposed.
 *
 * @param term         normalized query term
 * @param base         {@code 1.0} when the term matched the document, {@code 0.0} otherwise
 * @param fieldBoost   field-boost breakdown, present only when a non-neutral boost applied
 * @param contribution final value used in scoring ({@code base × boostFactor} or {@code base})
 */
public record BinaryTermScoring(
        String term,
        double base,
        Optional<FieldBoost> fieldBoost,
        double contribution
) implements TermScoring {

    public BinaryTermScoring {
        Objects.requireNonNull(term, "term cannot be null");
        Objects.requireNonNull(fieldBoost, "fieldBoost cannot be null");
        if (base != 0.0 && base != 1.0) {
            throw new IllegalArgumentException("Binary base must be 0.0 or 1.0, got: " + base);
        }
    }
}
