package codex.ir.ranking;

import codex.ir.Document;
import codex.ir.corpus.Corpora;
import codex.ir.corpus.Corpus;
import codex.ir.corpus.CorpusSnapshot;
import codex.ir.indexer.IndexSnapshot;
import codex.ir.indexer.Indexer;
import codex.ir.indexer.Indexers;
import codex.ir.indexer.InvertedIndex;
import codex.ir.indexer.InvertedIndexes;
import codex.ir.indexer.Posting;
import codex.ir.normalizer.Normalizer;
import codex.ir.normalizer.Normalizers;
import codex.ir.search.SearchResult;
import codex.ir.search.Searcher;
import codex.ir.search.Searchers;
import codex.ir.tokenizer.Tokenizer;
import codex.ir.tokenizer.Tokenizers;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for IR-4 — Field-Aware Ranking / Boosting.
 * <p>
 * Verifies that {@link RankingContext} and {@link FieldWeights} apply
 * a frequency-weighted boost via {@link Ranker#score(String, Posting, RankingContext)},
 * while neutral context and raw-content documents preserve whole-document baseline scores.
 */
class FieldAwareRankingTest {

    private static final Tokenizer TOKENIZER = Tokenizers.whitespace();
    private static final Normalizer NORMALIZER = Normalizers.english();

    // -----------------------------------------------------------------------
    // FieldWeights — record contract
    // -----------------------------------------------------------------------

    @Test
    void neutralFieldWeightsShouldReturnOnePointZeroForAnyField() {
        final FieldWeights fw = FieldWeights.neutral();
        assertEquals(1.0, fw.weightFor("title"));
        assertEquals(1.0, fw.weightFor("body"));
        assertEquals(1.0, fw.weightFor("unknown-field"));
        assertTrue(fw.isNeutral());
    }

    @Test
    void explicitFieldWeightsShouldReturnConfiguredValueAndDefaultForUnknown() {
        final FieldWeights fw = new FieldWeights(Map.of("title", 3.0, "body", 1.0));
        assertEquals(3.0, fw.weightFor("title"));
        assertEquals(1.0, fw.weightFor("body"));
        assertEquals(1.0, fw.weightFor("summary"), "Unknown field must default to 1.0");
        assertFalse(fw.isNeutral());
    }

    // -----------------------------------------------------------------------
    // RankingContext — neutral context gives identical scores
    // -----------------------------------------------------------------------

    @Test
    void neutralContextShouldGiveIdenticalScoreToBaseScore() {
        final Corpus corpus = Corpora.inMemory(Corpora.CorpusStatisticsRefreshMode.EAGER);
        final InvertedIndex index = InvertedIndexes.inMemory();
        indexTwoDocuments(corpus, index);

        final CorpusSnapshot cs = corpus.snapshot();
        final IndexSnapshot is = index.snapshot();
        final Ranker ranker = Rankers.tfIdf(cs, is);

        final Posting posting = is.getPostings("java").stream()
                .filter(p -> "title-doc".equals(p.documentId()))
                .findFirst().orElseThrow();

        final double base = ranker.score("java", posting);
        final double withNeutral = ranker.score("java", posting, RankingContext.neutral());

        assertEquals(base, withNeutral, 1e-12,
                "Neutral context must produce identical score to base score");
    }

    @Test
    void neutralContextShouldGiveIdenticalRankingToWholeDocumentBaseline() {
        final Corpus corpus = Corpora.inMemory(Corpora.CorpusStatisticsRefreshMode.EAGER);
        final InvertedIndex index = InvertedIndexes.inMemory();
        indexTwoDocuments(corpus, index);

        final CorpusSnapshot cs = corpus.snapshot();
        final IndexSnapshot is = index.snapshot();
        final Ranker ranker = Rankers.tfIdf(cs, is);

        final Searcher baseline = Searchers.lexical(is, cs, TOKENIZER, NORMALIZER, ranker);
        final Searcher neutral = Searchers.lexical(is, cs, TOKENIZER, NORMALIZER, ranker,
                RankingContext.neutral());

        final List<SearchResult> baseResults = baseline.searchDetailed("java");
        final List<SearchResult> neutralResults = neutral.searchDetailed("java");

        assertEquals(baseResults.size(), neutralResults.size());
        for (int i = 0; i < baseResults.size(); i++) {
            assertEquals(baseResults.get(i).documentId(), neutralResults.get(i).documentId());
            assertEquals(baseResults.get(i).score(), neutralResults.get(i).score(), 1e-12);
        }
    }

    // -----------------------------------------------------------------------
    // Field boost — title match ranks above body-only match
    // -----------------------------------------------------------------------

    @Test
    void titleBoostShouldRankTitleMatchAboveBodyOnlyMatch() {
        final Corpus corpus = Corpora.inMemory(Corpora.CorpusStatisticsRefreshMode.EAGER);
        final InvertedIndex index = InvertedIndexes.inMemory();
        final Indexer indexer = Indexers.lexical(corpus, index, TOKENIZER, NORMALIZER);

        // seed: no "java" — ensures df("java") < N so IDF > 0
        indexer.index(Document.builder()
                .id("seed-doc")
                .rawContent("python scripting language")
                .build());

        // doc-title: "java" only in title
        indexer.index(Document.builder()
                .id("doc-title")
                .field("title", "java platform")
                .field("body", "introductory guide for beginners")
                .build());

        // doc-body: "java" only in body
        indexer.index(Document.builder()
                .id("doc-body")
                .field("title", "programming guide")
                .field("body", "java language tutorial")
                .build());

        final CorpusSnapshot cs = corpus.snapshot();
        final IndexSnapshot is = index.snapshot();
        final Ranker ranker = Rankers.tfIdf(cs, is);

        final RankingContext context = RankingContext.of(
                new FieldWeights(Map.of("title", 3.0, "body", 1.0)));

        final Searcher boostedSearcher = Searchers.lexical(is, cs, TOKENIZER, NORMALIZER,
                ranker, context);

        final List<SearchResult> results = boostedSearcher.searchDetailed("java");
        assertFalse(results.isEmpty(), "Expected results for 'java'");
        assertEquals("doc-title", results.getFirst().documentId(),
                "Title-boosted searcher must rank the title-field match first");
    }

    @Test
    void titleBoostTfIdfShouldIncreaseScoreForTitleMatch() {
        final Corpus corpus = Corpora.inMemory(Corpora.CorpusStatisticsRefreshMode.EAGER);
        final InvertedIndex index = InvertedIndexes.inMemory();
        indexTwoDocuments(corpus, index);

        final CorpusSnapshot cs = corpus.snapshot();
        final IndexSnapshot is = index.snapshot();
        final Ranker ranker = Rankers.tfIdf(cs, is);

        final Posting titlePosting = is.getPostings("java").stream()
                .filter(p -> "title-doc".equals(p.documentId()))
                .findFirst().orElseThrow();

        final double base = ranker.score("java", titlePosting);
        final RankingContext boostedCtx = RankingContext.of(
                new FieldWeights(Map.of("title", 2.0)));
        final double boosted = ranker.score("java", titlePosting, boostedCtx);

        assertTrue(boosted > base,
                "Title boost must increase score for a term appearing in the title field");
        assertEquals(base * 2.0, boosted, 1e-12,
                "Score must be exactly base × 2.0 when term appears only in title with weight 2.0");
    }

    @Test
    void titleBoostBm25ShouldIncreaseScoreForTitleMatch() {
        final Corpus corpus = Corpora.inMemory(Corpora.CorpusStatisticsRefreshMode.EAGER);
        final InvertedIndex index = InvertedIndexes.inMemory();
        indexTwoDocuments(corpus, index);

        final CorpusSnapshot cs = corpus.snapshot();
        final IndexSnapshot is = index.snapshot();
        final Ranker ranker = Rankers.bm25(cs, is);

        final Posting titlePosting = is.getPostings("java").stream()
                .filter(p -> "title-doc".equals(p.documentId()))
                .findFirst().orElseThrow();

        final double base = ranker.score("java", titlePosting);
        final RankingContext boostedCtx = RankingContext.of(
                new FieldWeights(Map.of("title", 2.0)));
        final double boosted = ranker.score("java", titlePosting, boostedCtx);

        assertTrue(boosted > base,
                "Title boost must increase BM25 score for a term appearing in the title field");
        assertEquals(base * 2.0, boosted, 1e-12,
                "BM25 score must be exactly base × 2.0 when term appears only in title with weight 2.0");
    }

    // -----------------------------------------------------------------------
    // Boost factor formula — frequency-weighted average
    // -----------------------------------------------------------------------

    @Test
    void boostFactorShouldBeFrequencyWeightedAverageAcrossFields() {
        final Corpus corpus = Corpora.inMemory(Corpora.CorpusStatisticsRefreshMode.EAGER);
        final InvertedIndex index = InvertedIndexes.inMemory();
        final Indexer indexer = Indexers.lexical(corpus, index, TOKENIZER, NORMALIZER);

        // "java" appears once in title (weight 3.0) and once in body (weight 1.0)
        // boostFactor = (1×3.0 + 1×1.0) / (1+1) = 2.0
        indexer.index(Document.builder()
                .id("mixed-doc")
                .field("title", "java platform")
                .field("body", "java tutorial")
                .build());

        // second doc to give IDF a non-trivial value
        indexer.index(Document.builder()
                .id("other-doc")
                .field("title", "python guide")
                .build());

        final CorpusSnapshot cs = corpus.snapshot();
        final IndexSnapshot is = index.snapshot();
        final Ranker ranker = Rankers.tfIdf(cs, is);

        final Posting posting = is.getPostings("java").stream()
                .filter(p -> "mixed-doc".equals(p.documentId()))
                .findFirst().orElseThrow();

        final double base = ranker.score("java", posting);
        final RankingContext ctx = RankingContext.of(
                new FieldWeights(Map.of("title", 3.0, "body", 1.0)));
        final double boosted = ranker.score("java", posting, ctx);

        assertEquals(base * 2.0, boosted, 1e-12,
                "Expected boost factor = (1×3 + 1×1)/2 = 2.0 for equal frequency in title and body");
    }

    // -----------------------------------------------------------------------
    // Raw-content documents have empty fieldFrequencies — score unchanged
    // -----------------------------------------------------------------------

    @Test
    void rawContentDocumentShouldHaveUnchangedScoreWithAnyContext() {
        final Corpus corpus = Corpora.inMemory(Corpora.CorpusStatisticsRefreshMode.EAGER);
        final InvertedIndex index = InvertedIndexes.inMemory();
        final Indexer indexer = Indexers.lexical(corpus, index, TOKENIZER, NORMALIZER);

        indexer.index(Document.builder().id("raw-doc").rawContent("java search engine").build());
        indexer.index(Document.builder().id("raw-doc2").rawContent("python scripting language").build());

        final CorpusSnapshot cs = corpus.snapshot();
        final IndexSnapshot is = index.snapshot();
        final Ranker ranker = Rankers.tfIdf(cs, is);

        final Posting posting = is.getPostings("java").stream()
                .filter(p -> "raw-doc".equals(p.documentId()))
                .findFirst().orElseThrow();

        assertTrue(posting.fieldFrequencies().isEmpty(),
                "Raw-content document must have empty fieldFrequencies");

        final double base = ranker.score("java", posting);
        final RankingContext heavyBoost = RankingContext.of(
                new FieldWeights(Map.of("title", 100.0)));
        final double withBoost = ranker.score("java", posting, heavyBoost);

        assertEquals(base, withBoost, 1e-12,
                "Raw-content document score must be unchanged regardless of field weights");
    }

    @Test
    void unknownFieldShouldDefaultToWeightOnePointZeroAndNotAffectScore() {
        final Corpus corpus = Corpora.inMemory(Corpora.CorpusStatisticsRefreshMode.EAGER);
        final InvertedIndex index = InvertedIndexes.inMemory();
        final Indexer indexer = Indexers.lexical(corpus, index, TOKENIZER, NORMALIZER);

        // index with "custom" field — not in FieldWeights
        indexer.index(Document.builder()
                .id("custom-doc")
                .field("custom", "java platform")
                .build());
        indexer.index(Document.builder()
                .id("other-doc")
                .field("custom", "python scripting")
                .build());

        final CorpusSnapshot cs = corpus.snapshot();
        final IndexSnapshot is = index.snapshot();
        final Ranker ranker = Rankers.tfIdf(cs, is);

        final Posting posting = is.getPostings("java").stream()
                .filter(p -> "custom-doc".equals(p.documentId()))
                .findFirst().orElseThrow();

        final double base = ranker.score("java", posting);

        // context has no weight for "custom" → defaults to 1.0 → boostFactor = 1.0
        final RankingContext ctx = RankingContext.of(
                new FieldWeights(Map.of("title", 3.0)));
        final double withUnknownField = ranker.score("java", posting, ctx);

        assertEquals(base, withUnknownField, 1e-12,
                "Term in an unknown field must use weight 1.0 and leave the score unchanged");
    }

    // -----------------------------------------------------------------------
    // T-16 through T-21 — evaluate() exposes FieldBoost intermediates
    // -----------------------------------------------------------------------

    @Test
    void evaluateShouldExposeTitleBoostFactorWhenTermOccursOnlyInTitle() {
        // "java" in title (tf=1), title weight=3.0
        // boostFactor = (1 × 3.0) / 1 = 3.0
        // contribution = base × 3.0
        final Corpus corpus = Corpora.inMemory(Corpora.CorpusStatisticsRefreshMode.EAGER);
        final InvertedIndex index = InvertedIndexes.inMemory();
        final Indexer indexer = Indexers.lexical(corpus, index, TOKENIZER, NORMALIZER);

        indexer.index(Document.builder().id("doc-title").field("title", "java platform").field("body", "introductory guide").build());
        indexer.index(Document.builder().id("doc-other").rawContent("python scripting").build());

        final CorpusSnapshot cs = corpus.snapshot();
        final IndexSnapshot is = index.snapshot();
        final Ranker ranker = Rankers.tfIdf(cs, is);

        final Posting posting = is.getPostings("java").stream()
                .filter(p -> "doc-title".equals(p.documentId()))
                .findFirst().orElseThrow();

        final RankingContext ctx = RankingContext.of(new FieldWeights(Map.of("title", 3.0, "body", 1.0)));
        final TermScoring scoring = ranker.evaluate("java", posting, ctx);

        assertTrue(scoring.fieldBoost().isPresent(), "title occurrence must produce a field boost");
        final FieldBoost fb = scoring.fieldBoost().get();
        // title-only: boostFactor = (1 × 3.0) / 1 = 3.0
        assertEquals(3.0, fb.boostFactor(), 1e-9, "title-only boost factor must equal the title weight");
        assertEquals(scoring.base() * 3.0, scoring.contribution(), 1e-9,
                "contribution must be base × boostFactor");
    }

    @Test
    void evaluateShouldExposeBodyBoostFactorWhenTermOccursOnlyInBody() {
        // "java" in body (tf=1), body weight=2.0
        // boostFactor = (1 × 2.0) / 1 = 2.0
        // contribution = base × 2.0
        final Corpus corpus = Corpora.inMemory(Corpora.CorpusStatisticsRefreshMode.EAGER);
        final InvertedIndex index = InvertedIndexes.inMemory();
        final Indexer indexer = Indexers.lexical(corpus, index, TOKENIZER, NORMALIZER);

        indexer.index(Document.builder().id("doc-body").field("title", "programming guide").field("body", "java tutorial").build());
        indexer.index(Document.builder().id("doc-other").rawContent("python scripting").build());

        final CorpusSnapshot cs = corpus.snapshot();
        final IndexSnapshot is = index.snapshot();
        final Ranker ranker = Rankers.tfIdf(cs, is);

        final Posting posting = is.getPostings("java").stream()
                .filter(p -> "doc-body".equals(p.documentId()))
                .findFirst().orElseThrow();

        final RankingContext ctx = RankingContext.of(new FieldWeights(Map.of("title", 3.0, "body", 2.0)));
        final TermScoring scoring = ranker.evaluate("java", posting, ctx);

        assertTrue(scoring.fieldBoost().isPresent(), "body occurrence must produce a field boost");
        final FieldBoost fb = scoring.fieldBoost().get();
        // body-only: boostFactor = (1 × 2.0) / 1 = 2.0
        assertEquals(2.0, fb.boostFactor(), 1e-9, "body-only boost factor must equal the body weight");
        assertEquals(scoring.base() * 2.0, scoring.contribution(), 1e-9,
                "contribution must be base × boostFactor");
    }

    @Test
    void evaluateShouldComputeFrequencyWeightedAverageBoostForMultiFieldOccurrence() {
        // "java" in title (tf=1, weight=3.0) and body (tf=1, weight=1.0)
        // boostFactor = (1×3.0 + 1×1.0) / (1+1) = 4.0/2 = 2.0
        // contribution = base × 2.0
        final Corpus corpus = Corpora.inMemory(Corpora.CorpusStatisticsRefreshMode.EAGER);
        final InvertedIndex index = InvertedIndexes.inMemory();
        final Indexer indexer = Indexers.lexical(corpus, index, TOKENIZER, NORMALIZER);

        indexer.index(Document.builder().id("mixed-doc").field("title", "java platform").field("body", "java tutorial").build());
        indexer.index(Document.builder().id("other-doc").rawContent("python scripting").build());

        final CorpusSnapshot cs = corpus.snapshot();
        final IndexSnapshot is = index.snapshot();
        final Ranker ranker = Rankers.tfIdf(cs, is);

        final Posting posting = is.getPostings("java").stream()
                .filter(p -> "mixed-doc".equals(p.documentId()))
                .findFirst().orElseThrow();

        final RankingContext ctx = RankingContext.of(new FieldWeights(Map.of("title", 3.0, "body", 1.0)));
        final TermScoring scoring = ranker.evaluate("java", posting, ctx);

        assertTrue(scoring.fieldBoost().isPresent(), "multi-field occurrence must produce a field boost");
        final FieldBoost fb = scoring.fieldBoost().get();
        // boostFactor = (1×3.0 + 1×1.0) / 2 = 2.0
        assertEquals(2.0, fb.boostFactor(), 1e-9,
                "boost factor must be the frequency-weighted average of field weights");
        assertEquals(scoring.base() * 2.0, scoring.contribution(), 1e-9,
                "contribution must be base × boostFactor");
    }

    @Test
    void evaluateShouldMapUnknownFieldToWeightOneInEffectiveWeightsAndLeaveScoreUnchanged() {
        // "java" in "custom" field — not present in FieldWeights
        // effectiveWeights["custom"] == 1.0   (default for unknown field)
        // boostFactor = (1 × 1.0) / 1 = 1.0
        // contribution = base × 1.0 = base
        final Corpus corpus = Corpora.inMemory(Corpora.CorpusStatisticsRefreshMode.EAGER);
        final InvertedIndex index = InvertedIndexes.inMemory();
        final Indexer indexer = Indexers.lexical(corpus, index, TOKENIZER, NORMALIZER);

        indexer.index(Document.builder().id("custom-doc").field("custom", "java platform").build());
        indexer.index(Document.builder().id("other-doc").rawContent("python scripting").build());

        final CorpusSnapshot cs = corpus.snapshot();
        final IndexSnapshot is = index.snapshot();
        final Ranker ranker = Rankers.tfIdf(cs, is);

        final Posting posting = is.getPostings("java").stream()
                .filter(p -> "custom-doc".equals(p.documentId()))
                .findFirst().orElseThrow();

        // context has no weight for "custom" → must default to 1.0
        final RankingContext ctx = RankingContext.of(new FieldWeights(Map.of("title", 3.0)));
        final TermScoring scoring = ranker.evaluate("java", posting, ctx);

        assertTrue(scoring.fieldBoost().isPresent(), "non-neutral context with non-empty fieldFreqs must produce boost");
        final FieldBoost fb = scoring.fieldBoost().get();
        assertEquals(1.0, fb.effectiveWeights().get("custom"), 1e-9,
                "unknown field must be recorded with effective weight 1.0");
        assertEquals(1.0, fb.boostFactor(), 1e-9,
                "boost factor must be 1.0 when only field has effective weight 1.0");
        assertEquals(scoring.base(), scoring.contribution(), 1e-9,
                "contribution must equal base when boost factor is 1.0");
    }

    @Test
    void evaluateWithNeutralContextShouldReturnEmptyFieldBoostAndContributionEqualToBase() {
        // neutral RankingContext → TermScorings.computeFieldBoost returns empty
        final Corpus corpus = Corpora.inMemory(Corpora.CorpusStatisticsRefreshMode.EAGER);
        final InvertedIndex index = InvertedIndexes.inMemory();
        final Indexer indexer = Indexers.lexical(corpus, index, TOKENIZER, NORMALIZER);

        indexer.index(Document.builder().id("doc-title").field("title", "java platform").build());
        indexer.index(Document.builder().id("doc-other").rawContent("python scripting").build());

        final CorpusSnapshot cs = corpus.snapshot();
        final IndexSnapshot is = index.snapshot();
        final Ranker ranker = Rankers.tfIdf(cs, is);

        final Posting posting = is.getPostings("java").stream()
                .filter(p -> "doc-title".equals(p.documentId()))
                .findFirst().orElseThrow();

        final TermScoring scoring = ranker.evaluate("java", posting, RankingContext.neutral());

        assertTrue(scoring.fieldBoost().isEmpty(), "neutral context must produce empty fieldBoost");
        assertEquals(scoring.base(), scoring.contribution(), 1e-9,
                "contribution must equal base when no field boost is applied");
    }

    @Test
    void evaluateWithRawContentDocumentShouldReturnEmptyFieldBoostAndContributionEqualToBase() {
        // raw-content document has empty fieldFrequencies → no boost regardless of context weights
        final Corpus corpus = Corpora.inMemory(Corpora.CorpusStatisticsRefreshMode.EAGER);
        final InvertedIndex index = InvertedIndexes.inMemory();
        final Indexer indexer = Indexers.lexical(corpus, index, TOKENIZER, NORMALIZER);

        indexer.index(Document.builder().id("raw-doc").rawContent("java search engine").build());
        indexer.index(Document.builder().id("raw-doc2").rawContent("python scripting").build());

        final CorpusSnapshot cs = corpus.snapshot();
        final IndexSnapshot is = index.snapshot();
        final Ranker ranker = Rankers.tfIdf(cs, is);

        final Posting posting = is.getPostings("java").stream()
                .filter(p -> "raw-doc".equals(p.documentId()))
                .findFirst().orElseThrow();

        assertTrue(posting.fieldFrequencies().isEmpty(),
                "raw-content document must have empty fieldFrequencies");

        final RankingContext heavyCtx = RankingContext.of(new FieldWeights(Map.of("title", 100.0)));
        final TermScoring scoring = ranker.evaluate("java", posting, heavyCtx);

        assertTrue(scoring.fieldBoost().isEmpty(),
                "empty fieldFrequencies must produce empty fieldBoost regardless of context weights");
        assertEquals(scoring.base(), scoring.contribution(), 1e-9,
                "contribution must equal base when fieldFrequencies is empty");
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Indexes "title-doc" (java in title), "body-doc" (java in body), and a seed
     * document without "java". The seed ensures df("java") < N so IDF > 0 and
     * TF-IDF base scores are non-zero.
     */
    private static void indexTwoDocuments(final Corpus corpus, final InvertedIndex index) {
        final Indexer indexer = Indexers.lexical(corpus, index, TOKENIZER, NORMALIZER);
        indexer.index(Document.builder()
                .id("seed-doc")
                .rawContent("python scripting language")
                .build());
        indexer.index(Document.builder()
                .id("title-doc")
                .field("title", "java platform")
                .field("body", "introductory guide")
                .build());
        indexer.index(Document.builder()
                .id("body-doc")
                .field("title", "programming guide")
                .field("body", "java tutorial")
                .build());
    }
}
