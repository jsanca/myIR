package codex.ir.ranking;

import java.util.Objects;
import java.util.Optional;

/**
 * {@link TermScoring} produced by the TF-IDF ranker.
 *
 * <p>Exposes every intermediate needed to understand the contribution:
 * <pre>
 * sublinearTf = 1 + log(tf)
 * base        = sublinearTf × idf
 * contribution = base × boostFactor   (or base when no field boost)
 * </pre>
 *
 * <p>Raw corpus size ({@code N}) and document frequency ({@code df}) are not exposed;
 * {@code idf} is the invariant that matters for understanding the score.
 *
 * @param term         normalized query term
 * @param tf           raw term frequency from the posting
 * @param sublinearTf  {@code 1 + log(tf)} — sublinear term-frequency dampening
 * @param idf          {@code log(N / df)} — inverse document frequency
 * @param base         {@code sublinearTf × idf}
 * @param fieldBoost   field-boost breakdown, present only when a non-neutral boost applied
 * @param contribution final value used in scoring ({@code base × boostFactor} or {@code base})
 */
public record TfIdfTermScoring(
        String term,
        double tf,
        double sublinearTf,
        double idf,
        double base,
        Optional<FieldBoost> fieldBoost,
        double contribution
) implements TermScoring {

    public TfIdfTermScoring {
        Objects.requireNonNull(term, "term cannot be null");
        Objects.requireNonNull(fieldBoost, "fieldBoost cannot be null");
    }
}
