package codex.ir.search;

import codex.ir.Document;
import codex.ir.corpus.Corpora;
import codex.ir.corpus.Corpus;
import codex.ir.corpus.CorpusSnapshot;
import codex.ir.indexer.Indexer;
import codex.ir.indexer.Indexers;
import codex.ir.indexer.IndexSnapshot;
import codex.ir.indexer.InvertedIndex;
import codex.ir.indexer.InvertedIndexes;
import codex.ir.normalizer.Normalizer;
import codex.ir.normalizer.Normalizers;
import codex.ir.ranking.Ranker;
import codex.ir.ranking.RankingContext;
import codex.ir.ranking.Rankers;
import codex.ir.ranking.ScoreExplanation;
import codex.ir.ranking.TermScoring;
import codex.ir.tokenizer.Tokenizer;
import codex.ir.tokenizer.Tokenizers;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration and invariant tests for IR-5.3 — covers T-01 through T-06 and T-24 through T-30.
 */
class ExplainableSearcherTest {

    private static final double TOLERANCE = 1e-9;

    private static final Tokenizer TOKENIZER = Tokenizers.whitespace();
    private static final Normalizer NORMALIZER = Normalizers.english();

    // -----------------------------------------------------------------------
    // T-24 — SimpleSearcher implements ExplainableSearcher
    // -----------------------------------------------------------------------

    @Test
    void simpleSearcherShouldImplementExplainableSearcher() {
        final Searcher searcher = buildSearcher(Rankers.binary());
        assertInstanceOf(ExplainableSearcher.class, searcher,
                "SimpleSearcher must implement ExplainableSearcher");
    }

    // -----------------------------------------------------------------------
    // T-25 — VectorSearcher does not implement ExplainableSearcher
    // -----------------------------------------------------------------------

    @Test
    void vectorSearcherShouldNotImplementExplainableSearcher() {
        assertFalse(ExplainableSearcher.class.isAssignableFrom(VectorSearcher.class),
                "VectorSearcher must not implement ExplainableSearcher");
    }

    // -----------------------------------------------------------------------
    // T-26 — Searcher interface has no explain method
    // -----------------------------------------------------------------------

    @Test
    void searcherInterfaceShouldHaveNoExplainMethod() {
        final boolean hasExplain = Arrays.stream(Searcher.class.getMethods())
                .map(Method::getName)
                .anyMatch("explain"::equals);
        assertFalse(hasExplain, "Searcher interface must not declare an explain method");
    }

    // -----------------------------------------------------------------------
    // T-01 — explain returns ScoreExplanation for a matching document
    // -----------------------------------------------------------------------

    @Test
    void explainShouldReturnScoreExplanationForMatchingDocument() {
        final Searcher searcher = buildSearcherWithDocs(Rankers.tfIdf(
                threeDocCorpus().snapshot(),
                threeDocIndex(threeDocCorpus()).snapshot()));

        // rebuild with proper snapshots using helper
        final Corpus corpus = Corpora.inMemory(Corpora.CorpusStatisticsRefreshMode.EAGER);
        final InvertedIndex index = InvertedIndexes.inMemory();
        indexThreeDocs(corpus, index);
        final CorpusSnapshot cs = corpus.snapshot();
        final IndexSnapshot is = index.snapshot();
        final Ranker ranker = Rankers.tfIdf(cs, is);
        final ExplainableSearcher es = (ExplainableSearcher)
                Searchers.lexical(is, cs, TOKENIZER, NORMALIZER, ranker);

        final List<SearchResult> results = es.searchDetailed("java");
        assertFalse(results.isEmpty(), "search for 'java' must return results");

        final String docId = results.getFirst().documentId();
        final Optional<ScoreExplanation> explanationOpt = es.explain("java", docId);

        assertTrue(explanationOpt.isPresent(), "explain must return an explanation for a matching document");
        final ScoreExplanation explanation = explanationOpt.get();

        assertEquals("java", explanation.query());
        assertEquals(docId, explanation.documentId());
        assertTrue(explanation.score() > 0.0, "score must be positive");
        assertFalse(explanation.contributions().isEmpty(), "contributions must not be empty");
    }

    // -----------------------------------------------------------------------
    // T-02 — score conservation: explain.score() ≈ searchDetailed result score
    // -----------------------------------------------------------------------

    @Test
    void explainScoreShouldConserveSearchDetailedScoreForBinaryRanker() {
        assertScoreConservation(Rankers.binary());
    }

    @Test
    void explainScoreShouldConserveSearchDetailedScoreForTfIdfRanker() {
        final Corpus corpus = Corpora.inMemory(Corpora.CorpusStatisticsRefreshMode.EAGER);
        final InvertedIndex index = InvertedIndexes.inMemory();
        indexThreeDocs(corpus, index);
        assertScoreConservation(Rankers.tfIdf(corpus.snapshot(), index.snapshot()), corpus, index);
    }

    @Test
    void explainScoreShouldConserveSearchDetailedScoreForBm25Ranker() {
        final Corpus corpus = Corpora.inMemory(Corpora.CorpusStatisticsRefreshMode.EAGER);
        final InvertedIndex index = InvertedIndexes.inMemory();
        indexThreeDocs(corpus, index);
        assertScoreConservation(Rankers.bm25(corpus.snapshot(), index.snapshot()), corpus, index);
    }

    // -----------------------------------------------------------------------
    // T-03 — explanation excludes contributions for non-matching terms
    // -----------------------------------------------------------------------

    @Test
    void explainShouldExcludeContributionsForQueryTermsNotInDocument() {
        final Corpus corpus = Corpora.inMemory(Corpora.CorpusStatisticsRefreshMode.EAGER);
        final InvertedIndex index = InvertedIndexes.inMemory();
        indexThreeDocs(corpus, index);
        final CorpusSnapshot cs = corpus.snapshot();
        final IndexSnapshot is = index.snapshot();
        final ExplainableSearcher es = (ExplainableSearcher)
                Searchers.lexical(is, cs, TOKENIZER, NORMALIZER, Rankers.tfIdf(cs, is));

        // "java" is in some docs; "python" is NOT in any doc in this corpus
        final List<SearchResult> results = es.searchDetailed("java");
        final String docId = results.getFirst().documentId();

        // query has two terms; only "java" matches the document — "python" has no posting
        final Optional<ScoreExplanation> explanationOpt = es.explain("java python", docId);

        assertTrue(explanationOpt.isPresent());
        final ScoreExplanation explanation = explanationOpt.get();
        final List<String> contributingTerms = explanation.contributions().stream()
                .map(TermScoring::term)
                .toList();
        assertTrue(contributingTerms.contains("java"), "explanation must include contribution for matched term");
        assertFalse(contributingTerms.contains("python"), "explanation must not include contribution for unmatched term");
    }

    // -----------------------------------------------------------------------
    // T-04 — unknown documentId returns Optional.empty
    // -----------------------------------------------------------------------

    @Test
    void explainShouldReturnEmptyForUnknownDocumentId() {
        final Corpus corpus = Corpora.inMemory(Corpora.CorpusStatisticsRefreshMode.EAGER);
        final InvertedIndex index = InvertedIndexes.inMemory();
        indexThreeDocs(corpus, index);
        final CorpusSnapshot cs = corpus.snapshot();
        final IndexSnapshot is = index.snapshot();
        final ExplainableSearcher es = (ExplainableSearcher)
                Searchers.lexical(is, cs, TOKENIZER, NORMALIZER, Rankers.binary());

        assertTrue(es.explain("java", "no-such-doc-xyz").isEmpty(),
                "explain must return empty for a documentId not in the corpus");
    }

    // -----------------------------------------------------------------------
    // T-05 — null or blank query returns Optional.empty
    // -----------------------------------------------------------------------

    @Test
    void explainShouldReturnEmptyForNullQuery() {
        final ExplainableSearcher es = (ExplainableSearcher) buildSearcher(Rankers.binary());
        assertTrue(es.explain(null, "doc-1").isEmpty(),
                "explain must return empty for null query");
    }

    @Test
    void explainShouldReturnEmptyForBlankQuery() {
        final ExplainableSearcher es = (ExplainableSearcher) buildSearcher(Rankers.binary());
        assertTrue(es.explain("   ", "doc-1").isEmpty(),
                "explain must return empty for blank query");
    }

    // -----------------------------------------------------------------------
    // T-06 — all-stop-word query returns Optional.empty
    // -----------------------------------------------------------------------

    @Test
    void explainShouldReturnEmptyWhenQueryAnalyzesToEmpty() {
        final Corpus corpus = Corpora.inMemory(Corpora.CorpusStatisticsRefreshMode.EAGER);
        final InvertedIndex index = InvertedIndexes.inMemory();
        indexThreeDocs(corpus, index);
        final CorpusSnapshot cs = corpus.snapshot();
        final IndexSnapshot is = index.snapshot();
        final ExplainableSearcher es = (ExplainableSearcher)
                Searchers.lexical(is, cs, TOKENIZER, NORMALIZER, Rankers.binary());

        // "a" and "is" are English stop words → analyzeQuery returns empty
        assertTrue(es.explain("a is", "doc-java-1").isEmpty(),
                "explain must return empty when analyzed query is empty (all stop words)");
    }

    // -----------------------------------------------------------------------
    // T-27 — analyzeQuery produces same terms for searchDetailed and explain
    // -----------------------------------------------------------------------

    @Test
    void explainContributionTermsShouldMatchSearchDetailedMatchedTermsForMultiTermQuery() {
        final Corpus corpus = Corpora.inMemory(Corpora.CorpusStatisticsRefreshMode.EAGER);
        final InvertedIndex index = InvertedIndexes.inMemory();
        indexThreeDocs(corpus, index);
        final CorpusSnapshot cs = corpus.snapshot();
        final IndexSnapshot is = index.snapshot();
        final ExplainableSearcher es = (ExplainableSearcher)
                Searchers.lexical(is, cs, TOKENIZER, NORMALIZER, Rankers.binary());

        final List<SearchResult> results = es.searchDetailed("java search");
        assertFalse(results.isEmpty());
        final SearchResult topResult = results.getFirst();

        final Optional<ScoreExplanation> explanationOpt = es.explain("java search", topResult.documentId());
        assertTrue(explanationOpt.isPresent());

        // every term in the explanation must be a matched term from the search result
        final List<String> searchTerms = topResult.matchedTerms();
        explanationOpt.get().contributions().forEach(ts ->
                assertTrue(searchTerms.contains(ts.term()),
                        "explanation term '" + ts.term() + "' must be in searchDetailed matched terms"));
    }

    // -----------------------------------------------------------------------
    // T-28 — duplicate query terms: explanation mirrors searchDetailed behavior
    // -----------------------------------------------------------------------

    @Test
    void explainShouldFaithfullyMirrorDuplicateQueryTermBehavior() {
        final Corpus corpus = Corpora.inMemory(Corpora.CorpusStatisticsRefreshMode.EAGER);
        final InvertedIndex index = InvertedIndexes.inMemory();
        indexThreeDocs(corpus, index);
        final CorpusSnapshot cs = corpus.snapshot();
        final IndexSnapshot is = index.snapshot();
        final ExplainableSearcher es = (ExplainableSearcher)
                Searchers.lexical(is, cs, TOKENIZER, NORMALIZER, Rankers.binary());

        // "java java" — duplicate term
        final List<SearchResult> results = es.searchDetailed("java java");
        assertFalse(results.isEmpty());
        final String docId = results.getFirst().documentId();

        final Optional<ScoreExplanation> explanationOpt = es.explain("java java", docId);
        assertTrue(explanationOpt.isPresent());

        // both query tokens produce a contribution → 2 contributions
        assertEquals(2, explanationOpt.get().contributions().size(),
                "duplicate query term must produce two contributions");

        // explanation score must match search result score
        assertEquals(results.getFirst().score(), explanationOpt.get().score(), TOLERANCE,
                "explanation score must conserve searchDetailed score even with duplicate terms");
    }

    // -----------------------------------------------------------------------
    // T-29 — all-stop-word query: searchDetailed empty, explain Optional.empty
    // -----------------------------------------------------------------------

    @Test
    void allStopWordQueryShouldReturnEmptyFromBothSearchAndExplain() {
        final Corpus corpus = Corpora.inMemory(Corpora.CorpusStatisticsRefreshMode.EAGER);
        final InvertedIndex index = InvertedIndexes.inMemory();
        indexThreeDocs(corpus, index);
        final CorpusSnapshot cs = corpus.snapshot();
        final IndexSnapshot is = index.snapshot();
        final ExplainableSearcher es = (ExplainableSearcher)
                Searchers.lexical(is, cs, TOKENIZER, NORMALIZER, Rankers.binary());

        // "a" and "is" are English stop words
        assertTrue(es.searchDetailed("a is").isEmpty(),
                "searchDetailed must return empty for all-stop-word query");
        assertTrue(es.explain("a is", "doc-java-1").isEmpty(),
                "explain must return empty for all-stop-word query");
    }

    // -----------------------------------------------------------------------
    // T-30 — determinism: two explain calls return equal ScoreExplanation
    // -----------------------------------------------------------------------

    @Test
    void explainShouldBeDeterministic() {
        final Corpus corpus = Corpora.inMemory(Corpora.CorpusStatisticsRefreshMode.EAGER);
        final InvertedIndex index = InvertedIndexes.inMemory();
        indexThreeDocs(corpus, index);
        final CorpusSnapshot cs = corpus.snapshot();
        final IndexSnapshot is = index.snapshot();
        final ExplainableSearcher es = (ExplainableSearcher)
                Searchers.lexical(is, cs, TOKENIZER, NORMALIZER, Rankers.tfIdf(cs, is));

        final List<SearchResult> results = es.searchDetailed("java");
        final String docId = results.getFirst().documentId();

        final Optional<ScoreExplanation> first  = es.explain("java", docId);
        final Optional<ScoreExplanation> second = es.explain("java", docId);

        assertTrue(first.isPresent() && second.isPresent());
        assertEquals(first.get(), second.get(),
                "two explain calls with identical state must return equal ScoreExplanation");
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static Searcher buildSearcher(final Ranker ranker) {
        final Corpus corpus = Corpora.inMemory(Corpora.CorpusStatisticsRefreshMode.EAGER);
        final InvertedIndex index = InvertedIndexes.inMemory();
        indexThreeDocs(corpus, index);
        final CorpusSnapshot cs = corpus.snapshot();
        final IndexSnapshot is = index.snapshot();
        return Searchers.lexical(is, cs, TOKENIZER, NORMALIZER, ranker);
    }

    private static Searcher buildSearcherWithDocs(final Ranker ranker) {
        return buildSearcher(ranker);
    }

    /**
     * Shared fixture used across multiple tests.
     * Three documents: two contain "java", one does not ("python").
     * This gives df("java")=2, N=3 — IDF is non-trivial (>0).
     */
    private static void indexThreeDocs(final Corpus corpus, final InvertedIndex index) {
        final Indexer indexer = Indexers.lexical(corpus, index, TOKENIZER, NORMALIZER);
        indexer.index(Document.builder().id("doc-java-1").rawContent("java search engine").build());
        indexer.index(Document.builder().id("doc-java-2").rawContent("java platform framework").build());
        indexer.index(Document.builder().id("doc-seed").rawContent("python scripting language").build());
    }

    private static Corpus threeDocCorpus() {
        final Corpus corpus = Corpora.inMemory(Corpora.CorpusStatisticsRefreshMode.EAGER);
        final InvertedIndex index = InvertedIndexes.inMemory();
        indexThreeDocs(corpus, index);
        return corpus;
    }

    private static InvertedIndex threeDocIndex(final Corpus corpus) {
        final InvertedIndex index = InvertedIndexes.inMemory();
        indexThreeDocs(corpus, index);
        return index;
    }

    private void assertScoreConservation(final Ranker ranker) {
        final Corpus corpus = Corpora.inMemory(Corpora.CorpusStatisticsRefreshMode.EAGER);
        final InvertedIndex index = InvertedIndexes.inMemory();
        indexThreeDocs(corpus, index);
        assertScoreConservation(ranker, corpus, index);
    }

    private void assertScoreConservation(final Ranker ranker, final Corpus corpus, final InvertedIndex index) {
        final CorpusSnapshot cs = corpus.snapshot();
        final IndexSnapshot is = index.snapshot();
        final ExplainableSearcher es = (ExplainableSearcher)
                Searchers.lexical(is, cs, TOKENIZER, NORMALIZER, ranker);

        final List<SearchResult> results = es.searchDetailed("java search");
        assertFalse(results.isEmpty(), "score conservation test requires non-empty results");

        for (final SearchResult result : results) {
            final Optional<ScoreExplanation> explanationOpt = es.explain("java search", result.documentId());
            assertTrue(explanationOpt.isPresent(),
                    "explain must return a result for every document returned by searchDetailed");
            assertEquals(result.score(), explanationOpt.get().score(), TOLERANCE,
                    "explanation score must conserve searchDetailed score for document " + result.documentId());
        }
    }
}
