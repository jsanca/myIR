package codex.ir.ranking;

import codex.ir.indexer.Posting;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Package-private helper that computes {@link FieldBoost} and applies it.
 *
 * <p>Called by every {@link Ranker} implementation at the end of
 * {@code evaluate(term, posting, context)} so the boost formula lives in exactly one place.
 */
final class TermScorings {

    private TermScorings() {}

    /**
     * Returns the field-aware boost for a {@code (posting, context)} pair, or
     * {@link Optional#empty()} when no boost applies.
     *
     * <p>No boost when:
     * <ul>
     *   <li>the posting has empty {@code fieldFrequencies} (raw-content document), or</li>
     *   <li>the context's {@link FieldWeights} is neutral.</li>
     * </ul>
     *
     * <p>Unknown fields (present in the posting but absent from the configured weights) are
     * assigned an effective weight of {@code 1.0} and are visible in
     * {@link FieldBoost#effectiveWeights()}, making the default behavior observable.
     */
    static Optional<FieldBoost> computeFieldBoost(final Posting posting, final RankingContext context) {
        final Map<String, Integer> fieldFreqs = posting.fieldFrequencies();
        if (fieldFreqs.isEmpty() || context.fieldWeights().isNeutral()) {
            return Optional.empty();
        }
        final Map<String, Double> effectiveWeights = new HashMap<>();
        double weightedSum = 0.0;
        int totalFreq = 0;
        for (final Map.Entry<String, Integer> entry : fieldFreqs.entrySet()) {
            final int freq = entry.getValue();
            final double weight = context.fieldWeights().weightFor(entry.getKey());
            effectiveWeights.put(entry.getKey(), weight);
            weightedSum += freq * weight;
            totalFreq += freq;
        }
        if (totalFreq == 0) {
            return Optional.empty();
        }
        final double boostFactor = weightedSum / totalFreq;
        return Optional.of(new FieldBoost(fieldFreqs, effectiveWeights, weightedSum, totalFreq, boostFactor));
    }

    /** Returns {@code base * boostFactor} when a boost is present, otherwise {@code base}. */
    static double applyBoost(final double base, final Optional<FieldBoost> fieldBoost) {
        return fieldBoost.isPresent() ? base * fieldBoost.get().boostFactor() : base;
    }
}
