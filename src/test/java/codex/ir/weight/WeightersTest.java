package codex.ir.weight;

import codex.ir.Document;
import codex.ir.corpus.Corpora;
import codex.ir.corpus.Corpus;
import codex.ir.indexer.InvertedIndex;
import codex.ir.indexer.InvertedIndexes;
import codex.ir.indexer.Indexer;
import codex.ir.indexer.Indexers;
import codex.ir.normalizer.Normalizer;
import codex.ir.normalizer.Normalizers;
import codex.ir.tokenizer.Tokenizer;
import codex.ir.tokenizer.Tokenizers;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeightersTest {

    @Test
    void tfIdfShouldAssignHigherWeightToRarerTermWhenLocalFrequencyMatches() {
        final Tokenizer tokenizer = Tokenizers.whitespace();
        final Normalizer normalizer = Normalizers.english();
        final Corpus corpus = Corpora.inMemory(Corpora.CorpusStatisticsRefreshMode.EAGER);
        final InvertedIndex invertedIndex = InvertedIndexes.inMemory();
        final Indexer indexer = Indexers.lexical(corpus, invertedIndex, tokenizer, normalizer);
        final DocumentWeighter weighter = Weighters.tfIdf(tokenizer, invertedIndex);

        final Document doc1 = document("doc1", "java java search");
        final Document doc2 = document("doc2", "java programming");
        final Document doc3 = document("doc3", "index retrieval");

        indexer.index(doc1);
        indexer.index(doc2);
        indexer.index(doc3);

        final Map<String, Double> weights = weighter.weigh(corpus, doc1);

        assertFalse(weights.isEmpty(), "Expected TF-IDF weights to be generated for doc1");
        assertTrue(weights.containsKey("java"), "Expected TF-IDF weights to contain 'java'");
        assertTrue(weights.containsKey("search"), "Expected TF-IDF weights to contain 'search'");
        assertTrue(weights.get("search") > weights.get("java"),
                "Expected rarer term 'search' to have a higher TF-IDF weight than common term 'java', but java="
                        + weights.get("java") + ", search=" + weights.get("search"));
    }

    @Test
    void tfIdfShouldUseSublinearTfForRepeatedTerms() {
        final Tokenizer tokenizer = Tokenizers.whitespace();
        final Normalizer normalizer = Normalizers.english();
        final Corpus corpus = Corpora.inMemory(Corpora.CorpusStatisticsRefreshMode.EAGER);
        final InvertedIndex invertedIndex = InvertedIndexes.inMemory();
        final Indexer indexer = Indexers.lexical(corpus, invertedIndex, tokenizer, normalizer);
        final DocumentWeighter weighter = Weighters.tfIdf(tokenizer, invertedIndex);

        final Document repeatedTermDocument = document("doc1", "java java java");
        final Document singleOccurrenceDocument = document("doc2", "search engine");

        indexer.index(repeatedTermDocument);
        indexer.index(singleOccurrenceDocument);

        final Map<String, Double> repeatedWeights = weighter.weigh(corpus, repeatedTermDocument);
        final double javaWeight = repeatedWeights.get("java");

        assertTrue(javaWeight > 1.0,
                "Expected repeated term weight to be greater than the single-occurrence baseline, but was "
                        + javaWeight);
        assertTrue(javaWeight < 3.0,
                "Expected TF-IDF to apply sublinear TF instead of raw term frequency, but weight was "
                        + javaWeight);
    }

    @Test
    void tfIdfShouldReturnEmptyWeightsForBlankNormalizedContent() {
        final Corpus corpus = Corpora.inMemory(Corpora.CorpusStatisticsRefreshMode.EAGER);
        final InvertedIndex invertedIndex = InvertedIndexes.inMemory();
        final DocumentWeighter weighter = Weighters.tfIdf(Tokenizers.whitespace(), invertedIndex);
        final Document blankDocument = Document.builder()
                .id("blank-doc")
                .rawContent("   ")
                .normalizedContent("   ")
                .build();

        final Map<String, Double> weights = weighter.weigh(corpus, blankDocument);

        assertTrue(weights.isEmpty(), "Expected blank normalized content to produce no TF-IDF weights");
    }

    @Test
    void tfIdfShouldIgnoreTermsThatAreNotPresentInTheIndex() {
        final Tokenizer tokenizer = Tokenizers.whitespace();
        final Normalizer normalizer = Normalizers.english();
        final Corpus corpus = Corpora.inMemory(Corpora.CorpusStatisticsRefreshMode.EAGER);
        final InvertedIndex invertedIndex = InvertedIndexes.inMemory();
        final Indexer indexer = Indexers.lexical(corpus, invertedIndex, tokenizer, normalizer);
        final DocumentWeighter weighter = Weighters.tfIdf(tokenizer, invertedIndex);

        final Document indexedDocument = document("doc1", "java search");
        final Document queryLikeDocument = document("query-doc", "java phantomterm");

        indexer.index(indexedDocument);

        final Map<String, Double> weights = weighter.weigh(corpus, queryLikeDocument);

        assertTrue(weights.containsKey("java"), "Expected indexed term 'java' to have a TF-IDF weight");
        assertFalse(weights.containsKey("phantomterm"),
                "Expected term absent from the inverted index to be ignored by TF-IDF weighting");
        assertEquals(1, weights.size(),
                "Expected only indexed terms to contribute weights, but got " + weights);
    }

    private static Document document(final String id, final String text) {
        return Document.builder()
                .id(id)
                .rawContent(text)
                .normalizedContent(text)
                .build();
    }
}
