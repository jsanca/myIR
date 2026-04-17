package codex.ir.ranking;

import codex.ir.Document;
import codex.ir.indexer.*;
import codex.ir.normalizer.Normalizer;
import codex.ir.normalizer.Normalizers;
import codex.ir.corpus.Corpora;
import codex.ir.corpus.Corpus;
import codex.ir.tokenizer.Tokenizer;
import codex.ir.tokenizer.Tokenizers;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RankersTest {

    @Test
    void binaryRankerShouldReturnOneWhenPostingExists() {
        final Ranker ranker = Rankers.binary();
        final Posting posting = new Posting("doc-1", 3, List.of(1, 4, 7));

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
        final Posting posting = new Posting("doc-1", 1, List.of(2));

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
        final Ranker ranker = Rankers.tfIdf(corpus, invertedIndex);

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
        final Ranker ranker = Rankers.bm25(corpus, invertedIndex);

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
        final Ranker ranker = Rankers.bm25(corpus, invertedIndex);

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
        final Ranker ranker = Rankers.bm25(corpus, invertedIndex);

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
        final Ranker ranker = Rankers.tfIdf(corpus, invertedIndex);

        final double score = ranker.score("java", null);

        assertEquals(0.0, score);
    }

    @Test
    void tfIdfRankerShouldReturnNeutralIdfWhenTermNotIndexed() {
        final Corpus corpus = Corpora.inMemory();
        final InvertedIndex invertedIndex = InvertedIndexes.inMemory();
        final Ranker ranker = Rankers.tfIdf(corpus, invertedIndex);

        assertEquals(0.0, ranker.idf("java"));
        assertEquals(0.0, ranker.idf("search"));
    }
}
