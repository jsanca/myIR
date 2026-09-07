package codex.ir.ranking;

import java.util.Objects;

/**
 * Carries ranking configuration for a single search request.
 *
 * <p>At present a {@code RankingContext} holds only {@link FieldWeights}. Future slices
 * may extend it with additional per-request configuration (e.g. BM25F field lengths,
 * query-time boosts) without changing the {@link Ranker} contract.</p>
 *
 * <p>Use {@link #neutral()} when no field-specific boosting is required. The neutral
 * context guarantees identical scores to the whole-document ranking baseline.</p>
 */
public record RankingContext(FieldWeights fieldWeights) {

    public RankingContext {
        Objects.requireNonNull(fieldWeights, "fieldWeights cannot be null");
    }

    /** Returns a context with no field boosts — scores are identical to baseline ranking. */
    public static RankingContext neutral() {
        return new RankingContext(FieldWeights.neutral());
    }

    /** Returns a context using the supplied field weights. */
    public static RankingContext of(final FieldWeights fieldWeights) {
        return new RankingContext(fieldWeights);
    }
}
