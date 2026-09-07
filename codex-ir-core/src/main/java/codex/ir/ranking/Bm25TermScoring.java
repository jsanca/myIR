package codex.ir.ranking;

import java.util.Objects;
import java.util.Optional;

/**
 * {@link TermScoring} produced by the BM25 ranker.
 *
 * <p>Exposes the intermediates needed to understand term-frequency saturation,
 * document-length normalization, and the final contribution:
 * <pre>
 * normalization = 1 - b + b × (documentLength / averageDocumentLength)
 * base          = idf × (tf × (k1 + 1)) / (tf + k1 × normalization)
 * contribution  = base × boostFactor   (or base when no field boost)
 * </pre>
 *
 * <p>The BM25 IDF formula differs from classic TF-IDF:
 * {@code idf = log(1 + (N - df + 0.5) / (df + 0.5))}.
 *
 * <p>Raw {@code N} and {@code df} are not exposed; {@code idf} carries the information
 * needed to understand the contribution without exposing unnecessary intermediates.
 *
 * @param term                  normalized query term
 * @param tf                    raw term frequency from the posting
 * @param idf                   BM25 smoothed IDF for the term
 * @param documentLength        total term count of the document (from {@code Posting.termFrequency} aggregation)
 * @param averageDocumentLength corpus average document length at index time
 * @param k1                    term-frequency saturation parameter (hardcoded {@code 1.2})
 * @param b                     length-normalization parameter (hardcoded {@code 0.75})
 * @param normalization         {@code 1 - b + b × (documentLength / averageDocumentLength)}
 * @param base                  {@code idf × (tf × (k1 + 1)) / (tf + k1 × normalization)}
 * @param fieldBoost            field-boost breakdown, present only when a non-neutral boost applied
 * @param contribution          final value used in scoring ({@code base × boostFactor} or {@code base})
 */
public record Bm25TermScoring(
        String term,
        double tf,
        double idf,
        int documentLength,
        double averageDocumentLength,
        double k1,
        double b,
        double normalization,
        double base,
        Optional<FieldBoost> fieldBoost,
        double contribution
) implements TermScoring {

    public Bm25TermScoring {
        Objects.requireNonNull(term, "term cannot be null");
        Objects.requireNonNull(fieldBoost, "fieldBoost cannot be null");
    }
}
