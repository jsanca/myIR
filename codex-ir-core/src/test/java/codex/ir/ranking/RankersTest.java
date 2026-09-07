package codex.ir.ranking;

import codex.ir.Document;
import codex.ir.corpus.Corpora;
import codex.ir.corpus.Corpus;
import codex.ir.corpus.CorpusSnapshot;
import codex.ir.indexer.*;
import codex.ir.normalizer.Normalizer;
import codex.ir.normalizer.Normalizers;
import codex.ir.tokenizer.Tokenizer;
import codex.ir.tokenizer.Tokenizers;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;;

class RankersTest {

    @Test
    void binaryRankerShouldReturnOneWhenPostingExists() {
        final Ranker ranker = Rankers.binary();
        final Posting posting = new Posting("doc-1", 3, List.of(1, 4, 7), Map.of());

        final double score = ranker.score("java", posting);

        assertEquals(1.0, score);
    }

    @Test
    void binaryRankerShouldReturnZeroWhenPostingIsNull() {
        final Ranker ranker = Rankers.binary();

        final double score = ranker.score("java", null);

        assertEquals(0.0, score);
    }

    @Test
    void binaryRankerShouldReturnZeroWhenTermIsBlank() {
        final Ranker ranker = Rankers.binary();
        final Posting posting = new Posting("doc-1", 1, List.of(2), Map.of());

        final double score = ranker.score("   ", posting);

        assertEquals(0.0, score);
    }

    @Test
    void binaryRankerShouldAlwaysReturnNeutralIdf() {
        final Ranker ranker = Rankers.binary();

        assertEquals(1.0, ranker.idf("java"));
        assertEquals(1.0, ranker.idf("search"));
        assertEquals(1.0, ranker.idf("rare-term"));
    }

    @Test
    void tfIdfRankerShouldReturnTfMultipliedByIdf() {
        final Tokenizer tokenizer = Tokenizers.whitespace();
        final Normalizer normalizer = Normalizers.english();
        final Corpus corpus = Corpora.inMemory(Corpora.CorpusStatisticsRefreshMode.EAGER);
        final InvertedIndex invertedIndex = InvertedIndexes.inMemory();
        final Indexer indexer = Indexers.lexical(corpus, invertedIndex, tokenizer, normalizer);

        final String text1 = "Java is a programming language";
        final String text2 = "A search engine uses an inverted index";
        final String text3 = "Java can be used to build a search engine";

        final Document doc1 = Document.builder()
                .id("doc1.txt")
                .rawContent(text1)
                .normalizedContent(text1)
                .build();

        final Document doc2 = Document.builder()
                .id("doc2.txt")
                .rawContent(text2)
                .normalizedContent(text2)
                .build();

        final Document doc3 = Document.builder()
                .id("doc3.txt")
                .rawContent(text3)
                .normalizedContent(text3)
                .build();

        final List<Document> documents = List.of(doc1, doc2, doc3);

        for (final Document document : documents) {
            indexer.index(document);
        }

        final Ranker ranker = Rankers.tfIdf(corpus.snapshot(), invertedIndex.snapshot());
        final Posting javaPosting = invertedIndex.getPostings("java")
                .stream()
                .filter(posting -> "doc1.txt".equals(posting.documentId()))
                .findFirst()
                .orElseThrow();

        final double score = ranker.score("java", javaPosting);
        final double expectedIdf = Math.log(3.0 / 2.0);

        assertEquals(expectedIdf, score);
    }

    @Test
    void bm25RankerShouldReturnPositiveScoreForMatchingTerm() {
        final Tokenizer tokenizer = Tokenizers.whitespace();
        final Normalizer normalizer = Normalizers.english();
        final Corpus corpus = Corpora.inMemory(Corpora.CorpusStatisticsRefreshMode.EAGER);
        final InvertedIndex invertedIndex = InvertedIndexes.inMemory();
        final Indexer indexer = Indexers.lexical(corpus, invertedIndex, tokenizer, normalizer);

        final String text1 = "Java is a programming language";
        final String text2 = "A search engine uses an inverted index";
        final String text3 = "Java can be used to build a search engine";

        final Document doc1 = Document.builder()
                .id("doc1.txt")
                .rawContent(text1)
                .normalizedContent(text1)
                .build();

        final Document doc2 = Document.builder()
                .id("doc2.txt")
                .rawContent(text2)
                .normalizedContent(text2)
                .build();

        final Document doc3 = Document.builder()
                .id("doc3.txt")
                .rawContent(text3)
                .normalizedContent(text3)
                .build();

        final List<Document> documents = List.of(doc1, doc2, doc3);

        for (final Document document : documents) {
            indexer.index(document);
        }

        final Ranker ranker = Rankers.bm25(corpus.snapshot(), invertedIndex.snapshot());
        final Posting javaPosting = invertedIndex.getPostings("java")
                .stream()
                .filter(posting -> "doc1.txt".equals(posting.documentId()))
                .findFirst()
                .orElseThrow();

        final double score = ranker.score("java", javaPosting);

        assertTrue(score > 0.0);
    }

    @Test
    void bm25RankerShouldReturnZeroWhenPostingIsNull() {
        final Corpus corpus = Corpora.inMemory();
        final InvertedIndex invertedIndex = InvertedIndexes.inMemory();
        final Ranker ranker = Rankers.bm25(corpus.snapshot(), invertedIndex.snapshot());

        final double score = ranker.score("java", null);

        assertEquals(0.0, score);
    }

    @Test
    void bm25RankerShouldPenalizeLongerDocumentWhenTermFrequencyMatches() {
        final Tokenizer tokenizer = Tokenizers.whitespace();
        final Normalizer normalizer = Normalizers.english();
        final Corpus corpus = Corpora.inMemory(Corpora.CorpusStatisticsRefreshMode.EAGER);
        final InvertedIndex invertedIndex = InvertedIndexes.inMemory();
        final Indexer indexer = Indexers.lexical(corpus, invertedIndex, tokenizer, normalizer);

        final String shortText = "java code";
        final String longText = "java code architecture patterns testing deployment monitoring scaling pipelines";

        final Document shortDoc = Document.builder()
                .id("short-doc.txt")
                .rawContent(shortText)
                .normalizedContent(shortText)
                .build();

        final Document longDoc = Document.builder()
                .id("long-doc.txt")
                .rawContent(longText)
                .normalizedContent(longText)
                .build();

        indexer.index(shortDoc);
        indexer.index(longDoc);

        final Ranker ranker = Rankers.bm25(corpus.snapshot(), invertedIndex.snapshot());
        final Posting shortPosting = invertedIndex.getPostings("java")
                .stream()
                .filter(posting -> "short-doc.txt".equals(posting.documentId()))
                .findFirst()
                .orElseThrow();

        final Posting longPosting = invertedIndex.getPostings("java")
                .stream()
                .filter(posting -> "long-doc.txt".equals(posting.documentId()))
                .findFirst()
                .orElseThrow();

        final double shortScore = ranker.score("java", shortPosting);
        final double longScore = ranker.score("java", longPosting);

        System.out.println("BM25 shortScore=" + shortScore + ", longScore=" + longScore);

        assertTrue(shortScore > 0.0,
                "Expected short document BM25 score to be positive, but was " + shortScore);
        assertTrue(longScore > 0.0,
                "Expected long document BM25 score to be positive, but was " + longScore);
        assertTrue(shortScore > longScore,
                "Expected shorter document to score higher when term frequency matches, but shortScore="
                        + shortScore + ", longScore=" + longScore);
    }

    @Test
    void tfIdfRankerShouldReturnZeroWhenPostingIsNull() {
        final Corpus corpus = Corpora.inMemory();
        final InvertedIndex invertedIndex = InvertedIndexes.inMemory();
        final Ranker ranker = Rankers.tfIdf(corpus.snapshot(), invertedIndex.snapshot());

        final double score = ranker.score("java", null);

        assertEquals(0.0, score);
    }

    @Test
    void tfIdfRankerShouldReturnNeutralIdfWhenTermNotIndexed() {
        final Corpus corpus = Corpora.inMemory();
        final InvertedIndex invertedIndex = InvertedIndexes.inMemory();
        final Ranker ranker = Rankers.tfIdf(corpus.snapshot(), invertedIndex.snapshot());

        assertEquals(0.0, ranker.idf("java"));
        assertEquals(0.0, ranker.idf("search"));
    }

    @Test
    void bm25RankerShouldReturnZeroWhenDocumentHasNullLength() {
        final Corpus corpus = Corpora.inMemory(Corpora.CorpusStatisticsRefreshMode.EAGER);
        final InvertedIndex invertedIndex = InvertedIndexes.inMemory();

        final Document docWithLength = Document.builder()
                .id("doc-with-length")
                .rawContent("java search engine")
                .normalizedContent("java search engine")
                .length(3)
                .uniqueTerms(3)
                .build();

        final Document docWithoutLength = Document.builder()
                .id("doc-no-length")
                .rawContent("java")
                .build();

        corpus.add(docWithLength);
        corpus.add(docWithoutLength);

        invertedIndex.add("java", "doc-with-length", 0);
        invertedIndex.add("java", "doc-no-length", 0);

        final Ranker ranker = Rankers.bm25(corpus.snapshot(), invertedIndex.snapshot());

        final Posting posting = invertedIndex.getPostings("java")
                .stream()
                .filter(p -> "doc-no-length".equals(p.documentId()))
                .findFirst()
                .orElseThrow();

        final double score = ranker.score("java", posting);

        assertEquals(0.0, score,
                "Expected BM25 to return 0 for a document with null length metadata. "
                + "The document matches the query but cannot be scored because its metadata "
                + "lacks document length information.");
    }

    // -----------------------------------------------------------------------
    // T-08 — BinaryRanker: evaluate contribution == score (bit-equal)
    // -----------------------------------------------------------------------

    @Test
    void binaryRankerEvaluateShouldReturnBinaryTermScoringWithContributionEqualToScore() {
        final Ranker ranker = Rankers.binary();
        final Posting posting = new Posting("doc-1", 3, List.of(1, 4, 7), Map.of());

        final double score = ranker.score("java", posting);
        final TermScoring scoring = ranker.evaluate("java", posting, RankingContext.neutral());

        assertInstanceOf(BinaryTermScoring.class, scoring);
        assertEquals(score, scoring.contribution(), 0.0, "evaluate contribution must equal score exactly");
        assertEquals(1.0, scoring.base(), 0.0);
        assertTrue(scoring.fieldBoost().isEmpty());
    }

    // -----------------------------------------------------------------------
    // T-10 — TfIdfRanker: evaluate contribution == score (bit-equal)
    // -----------------------------------------------------------------------

    @Test
    void tfIdfRankerEvaluateShouldReturnTfIdfTermScoringWithContributionEqualToScore() {
        final Tokenizer tokenizer = Tokenizers.whitespace();
        final Normalizer normalizer = Normalizers.english();
        final Corpus corpus = Corpora.inMemory(Corpora.CorpusStatisticsRefreshMode.EAGER);
        final InvertedIndex invertedIndex = InvertedIndexes.inMemory();
        final Indexer indexer = Indexers.lexical(corpus, invertedIndex, tokenizer, normalizer);

        indexer.index(Document.builder().id("doc1.txt").rawContent("Java is a programming language").build());
        indexer.index(Document.builder().id("doc2.txt").rawContent("A search engine uses an inverted index").build());
        indexer.index(Document.builder().id("doc3.txt").rawContent("Java can be used to build a search engine").build());

        final IndexSnapshot is = invertedIndex.snapshot();
        final CorpusSnapshot cs = corpus.snapshot();
        final Ranker ranker = Rankers.tfIdf(cs, is);
        final Posting javaPosting = is.getPostings("java").stream()
                .filter(p -> "doc1.txt".equals(p.documentId()))
                .findFirst().orElseThrow();

        final double score = ranker.score("java", javaPosting);
        final TermScoring scoring = ranker.evaluate("java", javaPosting, RankingContext.neutral());

        assertInstanceOf(TfIdfTermScoring.class, scoring);
        assertEquals(score, scoring.contribution(), 0.0, "evaluate contribution must equal score exactly");
    }

    // -----------------------------------------------------------------------
    // T-13 — Bm25Ranker: k1 and b read from evaluate() return value
    // -----------------------------------------------------------------------

    @Test
    void bm25RankerEvaluateShouldReturnK1AndBFromRankerInternals() {
        final Corpus corpus = Corpora.inMemory(Corpora.CorpusStatisticsRefreshMode.EAGER);
        final InvertedIndex invertedIndex = InvertedIndexes.inMemory();

        corpus.add(Document.builder().id("doc-1").rawContent("java search").length(2).uniqueTerms(2).build());
        corpus.add(Document.builder().id("doc-2").rawContent("python code").length(2).uniqueTerms(2).build());
        invertedIndex.add("java", "doc-1", 0);
        invertedIndex.add("python", "doc-2", 0);

        final IndexSnapshot is = invertedIndex.snapshot();
        final Ranker ranker = Rankers.bm25(corpus.snapshot(), is);
        final Posting posting = is.getPostings("java").stream()
                .filter(p -> "doc-1".equals(p.documentId()))
                .findFirst().orElseThrow();

        final TermScoring scoring = ranker.evaluate("java", posting, RankingContext.neutral());

        assertInstanceOf(Bm25TermScoring.class, scoring);
        final Bm25TermScoring bm25 = (Bm25TermScoring) scoring;
        assertEquals(1.2,  bm25.k1(), 0.0, "k1 must be the hardcoded 1.2");
        assertEquals(0.75, bm25.b(),  0.0, "b must be the hardcoded 0.75");
    }

    // -----------------------------------------------------------------------
    // T-14 — Bm25Ranker: well-formed zero scoring when document length is 0
    // -----------------------------------------------------------------------

    @Test
    void bm25RankerEvaluateShouldReturnWellFormedZeroScoringWhenDocumentLengthIsZero() {
        final Corpus corpus = Corpora.inMemory(Corpora.CorpusStatisticsRefreshMode.EAGER);
        final InvertedIndex invertedIndex = InvertedIndexes.inMemory();

        corpus.add(Document.builder().id("doc-with-length").rawContent("java search engine").length(3).uniqueTerms(3).build());
        corpus.add(Document.builder().id("doc-no-length").rawContent("java").build());
        invertedIndex.add("java", "doc-with-length", 0);
        invertedIndex.add("java", "doc-no-length", 0);

        final IndexSnapshot is = invertedIndex.snapshot();
        final Ranker ranker = Rankers.bm25(corpus.snapshot(), is);
        final Posting posting = is.getPostings("java").stream()
                .filter(p -> "doc-no-length".equals(p.documentId()))
                .findFirst().orElseThrow();

        final TermScoring scoring = ranker.evaluate("java", posting, RankingContext.neutral());

        assertInstanceOf(Bm25TermScoring.class, scoring);
        assertEquals("java", scoring.term(), "term must be preserved even on zero contribution");
        assertEquals(0.0, scoring.contribution(), 0.0, "contribution must be 0.0 when document length is 0");
        assertEquals(0.0, ranker.score("java", posting), 0.0);
    }

    // -----------------------------------------------------------------------
    // T-15 — Bm25Ranker: evaluate contribution == score (bit-equal)
    // -----------------------------------------------------------------------

    @Test
    void bm25RankerEvaluateShouldReturnContributionEqualToScore() {
        final Tokenizer tokenizer = Tokenizers.whitespace();
        final Normalizer normalizer = Normalizers.english();
        final Corpus corpus = Corpora.inMemory(Corpora.CorpusStatisticsRefreshMode.EAGER);
        final InvertedIndex invertedIndex = InvertedIndexes.inMemory();
        final Indexer indexer = Indexers.lexical(corpus, invertedIndex, tokenizer, normalizer);

        indexer.index(Document.builder().id("doc1.txt").rawContent("Java is a programming language").build());
        indexer.index(Document.builder().id("doc2.txt").rawContent("Java can be used to build a search engine").build());

        final IndexSnapshot is = invertedIndex.snapshot();
        final CorpusSnapshot cs = corpus.snapshot();
        final Ranker ranker = Rankers.bm25(cs, is);
        final Posting posting = is.getPostings("java").stream()
                .filter(p -> "doc1.txt".equals(p.documentId()))
                .findFirst().orElseThrow();

        final double score = ranker.score("java", posting);
        final TermScoring scoring = ranker.evaluate("java", posting, RankingContext.neutral());

        assertInstanceOf(Bm25TermScoring.class, scoring);
        assertEquals(score, scoring.contribution(), 0.0, "evaluate contribution must equal score exactly");
    }

    @Test
    void bm25RankerShouldReturnPositiveScoreWhenDocumentHasLength() {
        final Corpus corpus = Corpora.inMemory(Corpora.CorpusStatisticsRefreshMode.EAGER);
        final InvertedIndex invertedIndex = InvertedIndexes.inMemory();

        final Document doc1 = Document.builder()
                .id("doc-1")
                .rawContent("java search engine")
                .normalizedContent("java search engine")
                .length(3)
                .uniqueTerms(3)
                .build();

        final Document doc2 = Document.builder()
                .id("doc-2")
                .rawContent("rust programming")
                .normalizedContent("rust programming")
                .length(2)
                .uniqueTerms(2)
                .build();

        corpus.add(doc1);
        corpus.add(doc2);

        invertedIndex.add("java", "doc-1", 0);
        invertedIndex.add("rust", "doc-2", 0);

        final Ranker ranker = Rankers.bm25(corpus.snapshot(), invertedIndex.snapshot());

        final Posting posting = invertedIndex.getPostings("java")
                .stream()
                .filter(p -> "doc-1".equals(p.documentId()))
                .findFirst()
                .orElseThrow();

        final double score = ranker.score("java", posting);

        assertTrue(score > 0.0,
                "Expected BM25 to return a positive score when document has valid length metadata");
    }
}
