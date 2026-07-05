package codex.ir.indexer;

import codex.ir.Document;
import codex.ir.corpus.Corpora;
import codex.ir.corpus.Corpus;
import codex.ir.normalizer.Normalizer;
import codex.ir.normalizer.Normalizers;
import codex.ir.ranking.Ranker;
import codex.ir.ranking.Rankers;
import codex.ir.search.SearchResult;
import codex.ir.search.Searcher;
import codex.ir.search.Searchers;
import codex.ir.tokenizer.Tokenizer;
import codex.ir.tokenizer.Tokenizers;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Tests for the current whole-document field aggregation contract
 * (see ADR-004) using lexical search only.
 * <p>
 * These tests verify that DocumentPreprocessor correctly aggregates field
 * values into normalizedContent and that lexical search finds terms from
 * any field. Per-field search is intentionally not tested because the
 * system does not yet support it.
 */
class FieldAwareIndexingTest {

    @Test
    void bodyOnlyDocumentShouldStillIndexAndSearchCorrectly() {
        final Tokenizer tokenizer = Tokenizers.whitespace();
        final Normalizer normalizer = Normalizers.english();
        final Corpus corpus = Corpora.inMemory(Corpora.CorpusStatisticsRefreshMode.EAGER);
        final InvertedIndex invertedIndex = InvertedIndexes.inMemory();
        final Indexer indexer = Indexers.lexical(corpus, invertedIndex, tokenizer, normalizer);

        final Document doc = Document.builder()
                .id("doc1")
                .rawContent("java search engine")
                .build();

        indexer.index(doc);

        final Ranker ranker = Rankers.tfIdf(corpus.snapshot(), invertedIndex.snapshot());
        final Searcher searcher = Searchers.lexical(invertedIndex.snapshot(), corpus.snapshot(), tokenizer, normalizer, ranker);

        final List<SearchResult> results = searcher.searchDetailed("search");
        assertFalse(results.isEmpty(), "Expected body-only document to be searchable");
        assertEquals("doc1", results.getFirst().document().id());
    }

    @Test
    void documentWithTitleAndBodyFieldsShouldIndexAndSearchCorrectly() {
        final Tokenizer tokenizer = Tokenizers.whitespace();
        final Normalizer normalizer = Normalizers.english();
        final Corpus corpus = Corpora.inMemory(Corpora.CorpusStatisticsRefreshMode.EAGER);
        final InvertedIndex invertedIndex = InvertedIndexes.inMemory();
        final Indexer indexer = Indexers.lexical(corpus, invertedIndex, tokenizer, normalizer);

        final Document doc = Document.builder()
                .id("doc1")
                .field("title", "Java Programming Guide")
                .field("body", "This guide covers search engines and indexing fundamentals")
                .build();

        indexer.index(doc);

        final Ranker ranker = Rankers.tfIdf(corpus.snapshot(), invertedIndex.snapshot());
        final Searcher searcher = Searchers.lexical(invertedIndex.snapshot(), corpus.snapshot(), tokenizer, normalizer, ranker);

        List<SearchResult> results = searcher.searchDetailed("programming");
        assertFalse(results.isEmpty(), "Expected term from title field to be searchable");
        assertEquals("doc1", results.getFirst().document().id());

        results = searcher.searchDetailed("fundamentals");
        assertFalse(results.isEmpty(), "Expected term from body field to be searchable");
        assertEquals("doc1", results.getFirst().document().id());
    }

    @Test
    void termOnlyInTitleShouldBeSearchable() {
        final Tokenizer tokenizer = Tokenizers.whitespace();
        final Normalizer normalizer = Normalizers.english();
        final Corpus corpus = Corpora.inMemory(Corpora.CorpusStatisticsRefreshMode.EAGER);
        final InvertedIndex invertedIndex = InvertedIndexes.inMemory();
        final Indexer indexer = Indexers.lexical(corpus, invertedIndex, tokenizer, normalizer);

        final Document doc = Document.builder()
                .id("doc1")
                .field("title", "Advanced Chromodynamics")
                .field("body", "This article discusses the basics of particle physics")
                .build();

        indexer.index(doc);

        final Ranker ranker = Rankers.tfIdf(corpus.snapshot(), invertedIndex.snapshot());
        final Searcher searcher = Searchers.lexical(invertedIndex.snapshot(), corpus.snapshot(), tokenizer, normalizer, ranker);

        final List<SearchResult> results = searcher.searchDetailed("chromodynamics");
        assertFalse(results.isEmpty(),
                "Expected term appearing only in title field to be searchable");
        assertEquals("doc1", results.getFirst().document().id());
    }

    @Test
    void documentWithAllBlankFieldsShouldFallbackToRawContent() {
        final Tokenizer tokenizer = Tokenizers.whitespace();
        final Normalizer normalizer = Normalizers.english();
        final Corpus corpus = Corpora.inMemory(Corpora.CorpusStatisticsRefreshMode.EAGER);
        final InvertedIndex invertedIndex = InvertedIndexes.inMemory();
        final Indexer indexer = Indexers.lexical(corpus, invertedIndex, tokenizer, normalizer);

        final Document doc = Document.builder()
                .id("doc1")
                .rawContent("fallback content for indexing")
                .field("title", "")
                .field("body", "   ")
                .build();

        indexer.index(doc);

        final Ranker ranker = Rankers.tfIdf(corpus.snapshot(), invertedIndex.snapshot());
        final Searcher searcher = Searchers.lexical(invertedIndex.snapshot(), corpus.snapshot(), tokenizer, normalizer, ranker);

        final List<SearchResult> results = searcher.searchDetailed("fallback");
        assertFalse(results.isEmpty(),
                "Expected rawContent fallback when all field values are blank");
        assertEquals("doc1", results.getFirst().document().id());
    }

    @Test
    void documentWithMixedBlankAndNonBlankFieldsShouldUseNonBlankValues() {
        final Tokenizer tokenizer = Tokenizers.whitespace();
        final Normalizer normalizer = Normalizers.english();
        final Corpus corpus = Corpora.inMemory(Corpora.CorpusStatisticsRefreshMode.EAGER);
        final InvertedIndex invertedIndex = InvertedIndexes.inMemory();
        final Indexer indexer = Indexers.lexical(corpus, invertedIndex, tokenizer, normalizer);

        final Document doc = Document.builder()
                .id("doc1")
                .field("title", "   ")
                .field("body", "")
                .field("summary", "distributed systems primer")
                .build();

        indexer.index(doc);

        final Ranker ranker = Rankers.tfIdf(corpus.snapshot(), invertedIndex.snapshot());
        final Searcher searcher = Searchers.lexical(invertedIndex.snapshot(), corpus.snapshot(), tokenizer, normalizer, ranker);

        final List<SearchResult> results = searcher.searchDetailed("distributed");
        assertFalse(results.isEmpty(),
                "Expected non-blank field values to be indexed when other fields are blank");
        assertEquals("doc1", results.getFirst().document().id());
    }

    @Test
    void documentWithOnlyFieldsAndNoRawContentShouldIndexCorrectly() {
        final Tokenizer tokenizer = Tokenizers.whitespace();
        final Normalizer normalizer = Normalizers.english();
        final Corpus corpus = Corpora.inMemory(Corpora.CorpusStatisticsRefreshMode.EAGER);
        final InvertedIndex invertedIndex = InvertedIndexes.inMemory();
        final Indexer indexer = Indexers.lexical(corpus, invertedIndex, tokenizer, normalizer);

        final Document doc = Document.builder()
                .id("doc1")
                .field("title", "Concurrent Programming in Java")
                .field("body", "Virtual threads simplify concurrent task execution")
                .build();

        indexer.index(doc);

        final Ranker ranker = Rankers.tfIdf(corpus.snapshot(), invertedIndex.snapshot());
        final Searcher searcher = Searchers.lexical(invertedIndex.snapshot(), corpus.snapshot(), tokenizer, normalizer, ranker);

        final List<SearchResult> results = searcher.searchDetailed("threads");
        assertFalse(results.isEmpty(),
                "Expected fields-based document with no rawContent to be searchable");
        assertEquals("doc1", results.getFirst().document().id());
    }
}
