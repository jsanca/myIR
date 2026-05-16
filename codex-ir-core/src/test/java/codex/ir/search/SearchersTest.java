package codex.ir.search;

import codex.ir.Document;
import codex.ir.corpus.Corpora;
import codex.ir.corpus.Corpus;
import codex.ir.indexer.InvertedIndex;
import codex.ir.indexer.InvertedIndexes;
import codex.ir.indexer.Indexer;
import codex.ir.indexer.Indexers;
import codex.ir.normalizer.Normalizer;
import codex.ir.normalizer.Normalizers;
import codex.ir.ranking.Ranker;
import codex.ir.ranking.Rankers;
import codex.ir.tokenizer.Tokenizer;
import codex.ir.tokenizer.Tokenizers;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchersTest {

    @Test
    void lexicalFactoryShouldReturnWorkingSearcher() {
        final Tokenizer tokenizer = Tokenizers.whitespace();
        final Normalizer normalizer = Normalizers.english();
        final Corpus corpus = Corpora.inMemory();
        final InvertedIndex invertedIndex = InvertedIndexes.inMemory();
        final Indexer indexer = Indexers.lexical(corpus, invertedIndex, tokenizer, normalizer);
        final Ranker ranker = Rankers.tfIdf(corpus, invertedIndex);

        final Searcher searcher = Searchers.lexical(invertedIndex, corpus, tokenizer, normalizer, ranker);
        assertNotNull(searcher, "Expected Searchers.lexical to return a non-null Searcher");

        final Document doc = Document.builder()
                .id("doc1")
                .rawContent("java search engine")
                .build();
        indexer.index(doc);

        final List<SearchResult> results = searcher.searchDetailed("search");
        assertFalse(results.isEmpty(), "Expected lexical searcher to find matching documents");
        assertEquals("doc1", results.getFirst().document().id());
    }

    @Test
    void lexicalSearcherShouldReturnEmptyListForNullQuery() {
        final Tokenizer tokenizer = Tokenizers.whitespace();
        final Normalizer normalizer = Normalizers.english();
        final Corpus corpus = Corpora.inMemory();
        final InvertedIndex invertedIndex = InvertedIndexes.inMemory();
        final Ranker ranker = Rankers.binary();
        final Searcher searcher = Searchers.lexical(invertedIndex, corpus, tokenizer, normalizer, ranker);

        final List<SearchResult> results = searcher.searchDetailed(null);

        assertTrue(results.isEmpty(),
                "Expected empty result list for null query instead of exception");
    }

    @Test
    void lexicalSearcherShouldReturnEmptyListForBlankQuery() {
        final Tokenizer tokenizer = Tokenizers.whitespace();
        final Normalizer normalizer = Normalizers.english();
        final Corpus corpus = Corpora.inMemory();
        final InvertedIndex invertedIndex = InvertedIndexes.inMemory();
        final Ranker ranker = Rankers.binary();
        final Searcher searcher = Searchers.lexical(invertedIndex, corpus, tokenizer, normalizer, ranker);

        final List<SearchResult> blankResults = searcher.searchDetailed("");
        assertTrue(blankResults.isEmpty(),
                "Expected empty result list for empty query string");

        final List<SearchResult> spacesResults = searcher.searchDetailed("   ");
        assertTrue(spacesResults.isEmpty(),
                "Expected empty result list for blank (whitespace-only) query string");
    }
}
