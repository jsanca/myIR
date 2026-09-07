package codex.ir.ranking;

import codex.ir.Document;
import codex.ir.corpus.Corpora;
import codex.ir.corpus.Corpus;
import codex.ir.corpus.CorpusSnapshot;
import codex.ir.indexer.IndexSnapshot;
import codex.ir.indexer.InvertedIndex;
import codex.ir.indexer.InvertedIndexes;
import codex.ir.indexer.Posting;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Formula tests for IR-5.2 — covers T-09, T-11, T-12.
 *
 * <p>Each test constructs a corpus with known parameters, calls {@link Ranker#evaluate},
 * and asserts that every intermediate field in the returned {@link TermScoring} matches
 * an independently hand-computed reference value.
 */
class RankingEvaluateTest {

    private static final double TOLERANCE = 1e-9;

    // -----------------------------------------------------------------------
    // T-09 — TF-IDF formula: independently hand-computed reference values
    // -----------------------------------------------------------------------

    @Test
    void tfIdfRankerEvaluateShouldMatchHandComputedFormula() {
        // Fixture: N=3 docs, "java" appears in 2 → df=2
        //   tf("java", doc-target) = 1  (one occurrence added directly)
        //   classicIdf  = log(N / df) = log(3 / 2) ≈ 0.4054651081081644
        //   sublinearTf = 1 + log(tf)  = 1 + log(1) = 1.0
        //   base        = sublinearTf × idf = 1.0 × log(3/2)
        //   contribution = base   (neutral context → no field boost)
        final Corpus corpus = Corpora.inMemory(Corpora.CorpusStatisticsRefreshMode.EAGER);
        final InvertedIndex index = InvertedIndexes.inMemory();

        corpus.add(Document.builder().id("doc-target").rawContent("java").length(1).uniqueTerms(1).build());
        corpus.add(Document.builder().id("doc-other").rawContent("java").length(1).uniqueTerms(1).build());
        corpus.add(Document.builder().id("doc-seed").rawContent("python").length(1).uniqueTerms(1).build());

        index.add("java", "doc-target", 0);
        index.add("java", "doc-other", 0);
        index.add("python", "doc-seed", 0);

        final CorpusSnapshot cs = corpus.snapshot();
        final IndexSnapshot is = index.snapshot();
        final Ranker ranker = Rankers.tfIdf(cs, is);

        final Posting posting = is.getPostings("java").stream()
                .filter(p -> "doc-target".equals(p.documentId()))
                .findFirst().orElseThrow();

        final TermScoring scoring = ranker.evaluate("java", posting, RankingContext.neutral());

        assertInstanceOf(TfIdfTermScoring.class, scoring);
        final TfIdfTermScoring tfidf = (TfIdfTermScoring) scoring;

        // hand-computed intermediates
        final double expectedIdf = Math.log(3.0 / 2.0);         // log(N/df) = log(1.5)
        final double expectedSublinearTf = 1.0 + Math.log(1.0); // 1 + log(1) = 1.0
        final double expectedBase = expectedSublinearTf * expectedIdf;

        assertEquals("java", tfidf.term());
        assertEquals(1.0, tfidf.tf(), TOLERANCE);
        assertEquals(expectedSublinearTf, tfidf.sublinearTf(), TOLERANCE);
        assertEquals(expectedIdf, tfidf.idf(), TOLERANCE);
        assertEquals(expectedBase, tfidf.base(), TOLERANCE);
        assertTrue(tfidf.fieldBoost().isEmpty(), "neutral context must produce no field boost");
        assertEquals(expectedBase, tfidf.contribution(), TOLERANCE);
        // invariant: contribution bit-equals score
        assertEquals(ranker.score("java", posting, RankingContext.neutral()), tfidf.contribution(), 0.0);
    }

    // -----------------------------------------------------------------------
    // T-11 — BM25 formula: independently hand-computed reference values
    // -----------------------------------------------------------------------

    @Test
    void bm25RankerEvaluateShouldMatchHandComputedFormula() {
        // Fixture: N=2 docs, both containing "java" → df=2, tf=1 each
        //   doc-short: dl=2,  doc-long: dl=10  →  avgdl = (2+10)/2 = 6.0
        //   k1=1.2, b=0.75
        //
        // BM25 IDF: log(1 + (N - df + 0.5) / (df + 0.5))
        //         = log(1 + (2 - 2 + 0.5) / (2 + 0.5))
        //         = log(1 + 0.5 / 2.5)
        //         = log(1.2)
        //
        // doc-short normalization: 1 - 0.75 + 0.75 × (2 / 6) = 0.25 + 0.25 = 0.5
        // doc-short base: log(1.2) × (1 × (1.2 + 1)) / (1 + 1.2 × 0.5)
        //               = log(1.2) × 2.2 / 1.6
        final Corpus corpus = Corpora.inMemory(Corpora.CorpusStatisticsRefreshMode.EAGER);
        final InvertedIndex index = InvertedIndexes.inMemory();

        corpus.add(Document.builder().id("doc-short").rawContent("java").length(2).uniqueTerms(1).build());
        corpus.add(Document.builder().id("doc-long").rawContent("java").length(10).uniqueTerms(1).build());

        index.add("java", "doc-short", 0);
        index.add("java", "doc-long", 0);

        final CorpusSnapshot cs = corpus.snapshot();
        final IndexSnapshot is = index.snapshot();

        assertEquals(6.0, cs.statistics().averageDocumentLength(), TOLERANCE,
                "corpus avgdl must equal (2+10)/2 = 6.0 for hand-computed values to apply");

        final Ranker ranker = Rankers.bm25(cs, is);
        final Posting postingShort = is.getPostings("java").stream()
                .filter(p -> "doc-short".equals(p.documentId()))
                .findFirst().orElseThrow();

        final TermScoring scoring = ranker.evaluate("java", postingShort, RankingContext.neutral());

        assertInstanceOf(Bm25TermScoring.class, scoring);
        final Bm25TermScoring bm25 = (Bm25TermScoring) scoring;

        final double expectedIdf  = Math.log(1.2);  // log(1 + 0.5/2.5)
        final double expectedNorm = 0.5;             // 1 - 0.75 + 0.75×(2/6)
        final double expectedBase = expectedIdf * (2.2 / 1.6); // idf × (tf×(k1+1)) / (tf + k1×norm)

        assertEquals("java", bm25.term());
        assertEquals(1.0,  bm25.tf(), TOLERANCE);
        assertEquals(expectedIdf,  bm25.idf(), TOLERANCE);
        assertEquals(2,    bm25.documentLength());
        assertEquals(6.0,  bm25.averageDocumentLength(), TOLERANCE);
        assertEquals(1.2,  bm25.k1(), TOLERANCE);
        assertEquals(0.75, bm25.b(), TOLERANCE);
        assertEquals(expectedNorm, bm25.normalization(), TOLERANCE);
        assertEquals(expectedBase, bm25.base(), TOLERANCE);
        assertTrue(bm25.fieldBoost().isEmpty());
        assertEquals(expectedBase, bm25.contribution(), TOLERANCE);
        // invariant: contribution bit-equals score
        assertEquals(ranker.score("java", postingShort, RankingContext.neutral()), bm25.contribution(), 0.0);
    }

    // -----------------------------------------------------------------------
    // T-12 — BM25 length normalization: shorter doc scores higher
    // -----------------------------------------------------------------------

    @Test
    void bm25RankerEvaluateShouldPenalizeLongerDocumentViaHigherNormalization() {
        // Same fixture as T-11.
        //   doc-short (dl=2):  normalization = 1 - 0.75 + 0.75×(2/6)  = 0.5
        //   doc-long  (dl=10): normalization = 1 - 0.75 + 0.75×(10/6) = 1.5
        //
        //   base_short = log(1.2) × 2.2 / 1.6   (larger)
        //   base_long  = log(1.2) × 2.2 / 2.8   (smaller)
        //
        //   normalization_short < normalization_long  ✓
        //   contribution_short  > contribution_long   ✓
        final Corpus corpus = Corpora.inMemory(Corpora.CorpusStatisticsRefreshMode.EAGER);
        final InvertedIndex index = InvertedIndexes.inMemory();

        corpus.add(Document.builder().id("doc-short").rawContent("java").length(2).uniqueTerms(1).build());
        corpus.add(Document.builder().id("doc-long").rawContent("java").length(10).uniqueTerms(1).build());

        index.add("java", "doc-short", 0);
        index.add("java", "doc-long", 0);

        final CorpusSnapshot cs = corpus.snapshot();
        final IndexSnapshot is = index.snapshot();
        final Ranker ranker = Rankers.bm25(cs, is);

        final Posting postingShort = is.getPostings("java").stream()
                .filter(p -> "doc-short".equals(p.documentId()))
                .findFirst().orElseThrow();
        final Posting postingLong = is.getPostings("java").stream()
                .filter(p -> "doc-long".equals(p.documentId()))
                .findFirst().orElseThrow();

        final Bm25TermScoring shortScoring = (Bm25TermScoring) ranker.evaluate("java", postingShort, RankingContext.neutral());
        final Bm25TermScoring longScoring  = (Bm25TermScoring) ranker.evaluate("java", postingLong,  RankingContext.neutral());

        assertEquals(0.5, shortScoring.normalization(), TOLERANCE, "short doc: norm = 1-0.75+0.75×(2/6) = 0.5");
        assertEquals(1.5, longScoring.normalization(),  TOLERANCE, "long doc:  norm = 1-0.75+0.75×(10/6) = 1.5");

        assertTrue(shortScoring.normalization() < longScoring.normalization(),
                "longer document must have larger normalization factor");
        assertTrue(shortScoring.contribution() > longScoring.contribution(),
                "shorter document must have larger BM25 contribution");
    }
}
