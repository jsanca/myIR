package codex.ir.ranking;

import java.util.Map;
import java.util.Objects;

/**
 * Breakdown of the field-aware boost applied to a single {@code (term, posting)} pair.
 *
 * <p>Attached to a {@link TermScoring} only when the {@link RankingContext} carried
 * explicit field weights <em>and</em> the posting contained field-frequency data.
 * When either condition is absent the {@link TermScoring} carries no {@code FieldBoost}
 * and its {@code contribution} equals its {@code base}.
 *
 * <p>The boost factor is computed as a frequency-weighted average of the per-field weights:
 * <pre>
 * boostFactor = weightedSum / totalFrequency
 *             = Σ(fieldFreq[f] × weight[f]) / Σ(fieldFreq[f])
 * </pre>
 *
 * <p>Fields not present in the configured {@link FieldWeights} receive an effective
 * weight of {@code 1.0}; they appear in {@link #effectiveWeights()} so the default
 * behavior is observable rather than silently hidden.
 *
 * @param fieldFrequencies  per-field occurrence counts copied from the posting
 * @param effectiveWeights  weight actually applied to each field (unknown fields → {@code 1.0})
 * @param weightedSum       {@code Σ(fieldFreq[f] × effectiveWeight[f])}
 * @param totalFrequency    {@code Σ(fieldFreq[f])}
 * @param boostFactor       {@code weightedSum / totalFrequency}
 */
public record FieldBoost(
        Map<String, Integer> fieldFrequencies,
        Map<String, Double> effectiveWeights,
        double weightedSum,
        int totalFrequency,
        double boostFactor
) {
    public FieldBoost {
        Objects.requireNonNull(fieldFrequencies, "fieldFrequencies cannot be null");
        Objects.requireNonNull(effectiveWeights, "effectiveWeights cannot be null");
        fieldFrequencies = Map.copyOf(fieldFrequencies);
        effectiveWeights = Map.copyOf(effectiveWeights);
    }
}
