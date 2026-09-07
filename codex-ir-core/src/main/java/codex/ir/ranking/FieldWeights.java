package codex.ir.ranking;

import java.util.Map;
import java.util.Objects;

/**
 * Field-specific boost multipliers for ranking.
 *
 * <p>A {@code FieldWeights} instance maps field names to positive boost multipliers.
 * During field-aware scoring the multiplier for each field is applied proportionally
 * to how frequently the query term appeared in that field (see
 * {@link Ranker#score(String, codex.ir.indexer.Posting, RankingContext)}).
 * Fields not present in the map receive a neutral weight of {@code 1.0}, leaving
 * their contribution unchanged.</p>
 *
 * <p>The {@link #neutral()} factory produces an instance with no explicit weights;
 * every field defaults to {@code 1.0}, so scores are identical to whole-document
 * ranking.</p>
 */
public record FieldWeights(Map<String, Double> weights) {

    public FieldWeights {
        Objects.requireNonNull(weights, "weights cannot be null");
        weights = Map.copyOf(weights);
    }

    /** Returns a {@code FieldWeights} that leaves all scores unchanged. */
    public static FieldWeights neutral() {
        return new FieldWeights(Map.of());
    }

    /**
     * Returns the boost multiplier for the given field.
     * Fields not explicitly configured return {@code 1.0}.
     */
    public double weightFor(final String fieldName) {
        return weights.getOrDefault(fieldName, 1.0);
    }

    /** Returns {@code true} when no explicit weights are configured. */
    public boolean isNeutral() {
        return weights.isEmpty();
    }
}
