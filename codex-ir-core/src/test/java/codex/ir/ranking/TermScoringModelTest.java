package codex.ir.ranking;

import codex.ir.Document;
import codex.ir.search.SearchResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * IR-5.1 — Explanation domain model.
 *
 * Covers T-07, T-13, T-19, T-23 from the canonical use-case test matrix, plus
 * structural instantiation and defensive-copy contracts for all six new types.
 */
class TermScoringModelTest {

    /** Shared numerical tolerance for formula and invariant assertions (per use-case doc). */
    static final double TOLERANCE = 1e-9;

    // -----------------------------------------------------------------------
    // T-07 — BinaryTermScoring shape
    // UC-2: binary exposes term, base==1.0, contribution==1.0; no TF/IDF fields
    // -----------------------------------------------------------------------

    @Test
    void binaryTermScoringShouldExposeTermBaseAndContributionForMatchedTerm() {
        final BinaryTermScoring scoring = new BinaryTermScoring("java", 1.0, Optional.empty(), 1.0);

        assertEquals("java", scoring.term());
        assertEquals(1.0, scoring.base(), TOLERANCE);
        assertEquals(1.0, scoring.contribution(), TOLERANCE);
        assertTrue(scoring.fieldBoost().isEmpty());
    }

    @Test
    void binaryTermScoringShouldExposeZeroBaseAndContributionForNoMatch() {
        final BinaryTermScoring scoring = new BinaryTermScoring("java", 0.0, Optional.empty(), 0.0);

        assertEquals("java", scoring.term());
        assertEquals(0.0, scoring.base(), TOLERANCE);
        assertEquals(0.0, scoring.contribution(), TOLERANCE);
    }

    @Test
    void binaryTermScoringShouldRejectInvalidBase() {
        assertThrows(IllegalArgumentException.class,
                () -> new BinaryTermScoring("java", 0.5, Optional.empty(), 0.5));
    }

    @Test
    void binaryTermScoringShouldRejectNullTerm() {
        assertThrows(NullPointerException.class,
                () -> new BinaryTermScoring(null, 1.0, Optional.empty(), 1.0));
    }

    @Test
    void binaryTermScoringShouldRejectNullFieldBoost() {
        assertThrows(NullPointerException.class,
                () -> new BinaryTermScoring("java", 1.0, null, 1.0));
    }

    @Test
    void binaryTermScoringShouldExposeFieldBoostWhenPresent() {
        final FieldBoost boost = new FieldBoost(
                Map.of("title", 1),
                Map.of("title", 3.0),
                3.0, 1, 3.0);
        final BinaryTermScoring scoring = new BinaryTermScoring("java", 1.0, Optional.of(boost), 3.0);

        assertTrue(scoring.fieldBoost().isPresent());
        assertEquals(3.0, scoring.contribution(), TOLERANCE);
    }

    // -----------------------------------------------------------------------
    // TfIdfTermScoring structural tests
    // -----------------------------------------------------------------------

    @Test
    void tfIdfTermScoringShouldInstantiateAndExposeAllFields() {
        final TfIdfTermScoring scoring = new TfIdfTermScoring(
                "java", 3.0, 2.0986, 1.6094, 3.375, Optional.empty(), 3.375);

        assertEquals("java", scoring.term());
        assertEquals(3.0, scoring.tf(), TOLERANCE);
        assertEquals(2.0986, scoring.sublinearTf(), TOLERANCE);
        assertEquals(1.6094, scoring.idf(), TOLERANCE);
        assertEquals(3.375, scoring.base(), TOLERANCE);
        assertTrue(scoring.fieldBoost().isEmpty());
        assertEquals(3.375, scoring.contribution(), TOLERANCE);
    }

    @Test
    void tfIdfTermScoringShouldRejectNullTerm() {
        assertThrows(NullPointerException.class,
                () -> new TfIdfTermScoring(null, 1.0, 1.0, 1.0, 1.0, Optional.empty(), 1.0));
    }

    @Test
    void tfIdfTermScoringShouldRejectNullFieldBoost() {
        assertThrows(NullPointerException.class,
                () -> new TfIdfTermScoring("java", 1.0, 1.0, 1.0, 1.0, null, 1.0));
    }

    // -----------------------------------------------------------------------
    // T-13 — Bm25TermScoring exposes k1 and b
    // UC-2: BM25 exposes k1=1.2 and b=0.75
    // -----------------------------------------------------------------------

    @Test
    void bm25TermScoringShouldExposeK1AndB() {
        final Bm25TermScoring scoring = new Bm25TermScoring(
                "java", 2.0, 1.386, 10, 8.0, 1.2, 0.75, 1.6875, 2.0, Optional.empty(), 2.0);

        // T-13: k1 and b are carried on the record and readable
        assertEquals(1.2, scoring.k1(), TOLERANCE);
        assertEquals(0.75, scoring.b(), TOLERANCE);
    }

    @Test
    void bm25TermScoringShouldInstantiateAndExposeAllFields() {
        final Bm25TermScoring scoring = new Bm25TermScoring(
                "java", 2.0, 1.386, 10, 8.0, 1.2, 0.75, 1.6875, 2.0, Optional.empty(), 2.0);

        assertEquals("java", scoring.term());
        assertEquals(2.0, scoring.tf(), TOLERANCE);
        assertEquals(1.386, scoring.idf(), TOLERANCE);
        assertEquals(10, scoring.documentLength());
        assertEquals(8.0, scoring.averageDocumentLength(), TOLERANCE);
        assertEquals(1.6875, scoring.normalization(), TOLERANCE);
        assertEquals(2.0, scoring.base(), TOLERANCE);
        assertTrue(scoring.fieldBoost().isEmpty());
        assertEquals(2.0, scoring.contribution(), TOLERANCE);
    }

    @Test
    void bm25TermScoringShouldRejectNullTerm() {
        assertThrows(NullPointerException.class,
                () -> new Bm25TermScoring(null, 1.0, 1.0, 5, 5.0, 1.2, 0.75, 1.0, 1.0, Optional.empty(), 1.0));
    }

    @Test
    void bm25TermScoringShouldRejectNullFieldBoost() {
        assertThrows(NullPointerException.class,
                () -> new Bm25TermScoring("java", 1.0, 1.0, 5, 5.0, 1.2, 0.75, 1.0, 1.0, null, 1.0));
    }

    // -----------------------------------------------------------------------
    // T-19 — FieldBoost: unknown-field default visible in effectiveWeights
    // UC-3: unknown field mapped to 1.0 in effectiveWeights
    // -----------------------------------------------------------------------

    @Test
    void fieldBoostShouldMakeUnknownFieldWeightObservable() {
        // Posting had an "unknown" field not in the configured FieldWeights.
        // The boost helper must record it with 1.0 in effectiveWeights.
        final FieldBoost boost = new FieldBoost(
                Map.of("unknown", 2),
                Map.of("unknown", 1.0),   // default applied
                2.0,                       // weightedSum = 2 × 1.0
                2,                         // totalFrequency
                1.0                        // boostFactor = 2.0/2
        );

        // T-19: the unknown field appears in effectiveWeights with value 1.0
        assertTrue(boost.effectiveWeights().containsKey("unknown"));
        assertEquals(1.0, boost.effectiveWeights().get("unknown"), TOLERANCE);
        assertEquals(1.0, boost.boostFactor(), TOLERANCE);
    }

    @Test
    void fieldBoostShouldInstantiateAndExposeAllFields() {
        final FieldBoost boost = new FieldBoost(
                Map.of("title", 1, "body", 2),
                Map.of("title", 3.0, "body", 1.0),
                5.0,   // weightedSum = 1×3 + 2×1
                3,     // totalFrequency
                5.0 / 3.0);

        assertEquals(Map.of("title", 1, "body", 2), boost.fieldFrequencies());
        assertEquals(Map.of("title", 3.0, "body", 1.0), boost.effectiveWeights());
        assertEquals(5.0, boost.weightedSum(), TOLERANCE);
        assertEquals(3, boost.totalFrequency());
        assertEquals(5.0 / 3.0, boost.boostFactor(), TOLERANCE);
    }

    @Test
    void fieldBoostShouldRejectNullFieldFrequencies() {
        assertThrows(NullPointerException.class,
                () -> new FieldBoost(null, Map.of(), 0.0, 0, 1.0));
    }

    @Test
    void fieldBoostShouldRejectNullEffectiveWeights() {
        assertThrows(NullPointerException.class,
                () -> new FieldBoost(Map.of(), null, 0.0, 0, 1.0));
    }

    @Test
    void fieldBoostShouldDefensiveCopyFieldFrequencies() {
        final Map<String, Integer> mutable = new HashMap<>(Map.of("title", 1));
        final FieldBoost boost = new FieldBoost(mutable, Map.of("title", 2.0), 2.0, 1, 2.0);

        mutable.put("body", 99);  // mutate after construction

        assertFalse(boost.fieldFrequencies().containsKey("body"),
                "FieldBoost.fieldFrequencies must be an independent copy");
    }

    @Test
    void fieldBoostShouldDefensiveCopyEffectiveWeights() {
        final Map<String, Double> mutable = new HashMap<>(Map.of("title", 3.0));
        final FieldBoost boost = new FieldBoost(Map.of("title", 1), mutable, 3.0, 1, 3.0);

        mutable.put("body", 99.0);

        assertFalse(boost.effectiveWeights().containsKey("body"),
                "FieldBoost.effectiveWeights must be an independent copy");
    }

    // -----------------------------------------------------------------------
    // ScoreExplanation structural tests
    // -----------------------------------------------------------------------

    @Test
    void scoreExplanationShouldInstantiateAndExposeAllFields() {
        final BinaryTermScoring ts = new BinaryTermScoring("java", 1.0, Optional.empty(), 1.0);
        final ScoreExplanation explanation = new ScoreExplanation("java", "doc-1", 1.0, List.of(ts));

        assertEquals("java", explanation.query());
        assertEquals("doc-1", explanation.documentId());
        assertEquals(1.0, explanation.score(), TOLERANCE);
        assertEquals(1, explanation.contributions().size());
        assertEquals("java", explanation.contributions().get(0).term());
    }

    @Test
    void scoreExplanationShouldRejectNullQuery() {
        assertThrows(NullPointerException.class,
                () -> new ScoreExplanation(null, "doc-1", 1.0, List.of()));
    }

    @Test
    void scoreExplanationShouldRejectNullDocumentId() {
        assertThrows(NullPointerException.class,
                () -> new ScoreExplanation("java", null, 1.0, List.of()));
    }

    @Test
    void scoreExplanationShouldRejectNullContributions() {
        assertThrows(NullPointerException.class,
                () -> new ScoreExplanation("java", "doc-1", 1.0, null));
    }

    @Test
    void scoreExplanationShouldDefensiveCopyContributions() {
        final BinaryTermScoring ts = new BinaryTermScoring("java", 1.0, Optional.empty(), 1.0);
        final List<TermScoring> mutable = new ArrayList<>();
        mutable.add(ts);
        final ScoreExplanation explanation = new ScoreExplanation("java", "doc-1", 1.0, mutable);

        mutable.add(new BinaryTermScoring("spring", 1.0, Optional.empty(), 1.0));

        assertEquals(1, explanation.contributions().size(),
                "ScoreExplanation.contributions must be an independent copy");
    }

    // -----------------------------------------------------------------------
    // T-23 — SearchResult record shape unchanged
    // UC-4: existing SearchResult contract is unmodified by IR-5
    // -----------------------------------------------------------------------

    @Test
    void searchResultRecordShouldRetainItsPreIr5Shape() {
        // Verify the four-component SearchResult record compiles and accessors work.
        // This confirms IR-5.1 did not modify SearchResult.
        final Document doc = Document.builder().rawContent("hello world").build();
        final SearchResult result = new SearchResult("doc-1", doc, 2.5, List.of("hello", "world"));

        assertEquals("doc-1", result.documentId());
        assertEquals(doc, result.document());
        assertEquals(2.5, result.score(), TOLERANCE);
        assertEquals(List.of("hello", "world"), result.matchedTerms());
    }
}
