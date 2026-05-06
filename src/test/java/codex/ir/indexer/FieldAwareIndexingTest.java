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
import codex.ir.search.SimpleSearcher;
import codex.ir.tokenizer.Tokenizer;
import codex.ir.tokenizer.Tokenizers;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class FieldAwareIndexingTest {

    @Test
    void bodyOnlyDocumentShouldStillIndexAndSearchCorrectly() {
        final Tokenizer tokenizer = Tokenizers.whitespace();
        final Normalizer normalizer = Normalizers.english();
        final Corpus corpus = Corpora.inMemory(Corpora.CorpusStatisticsRefreshMode.EAGER);
        final InvertedIndex invertedIndex = InvertedIndexes.inMemory();
        final Indexer indexer = Indexers.lexical(corpus, invertedIndex, tokenizer, normalizer);
        final Ranker ranker = Rankers.tfIdf(corpus, invertedIndex);
        final Searcher searcher = new SimpleSearcher(invertedIndex, corpus, tokenizer, normalizer, ranker);

        final Document doc = Document.builder()
                .id("doc1")
                .rawContent("java search engine")
                .build();

        indexer.index(doc);

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
        final Ranker ranker = Rankers.tfIdf(corpus, invertedIndex);
        final Searcher searcher = new SimpleSearcher(invertedIndex, corpus, tokenizer, normalizer, ranker);

        final Document doc = Document.builder()
                .id("doc1")
                .field("title", "Java Programming Guide")
                .field("body", "This guide covers search engines and indexing fundamentals")
                .build();

        indexer.index(doc);

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
        final Ranker ranker = Rankers.tfIdf(corpus, invertedIndex);
        final Searcher searcher = new SimpleSearcher(invertedIndex, corpus, tokenizer, normalizer, ranker);

        final Document doc = Document.builder()
                .id("doc1")
                .field("title", "Advanced Chromodynamics")
                .field("body", "This article discusses the basics of particle physics")
                .build();

        indexer.index(doc);

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
        final Ranker ranker = Rankers.tfIdf(corpus, invertedIndex);
        final Searcher searcher = new SimpleSearcher(invertedIndex, corpus, tokenizer, normalizer, ranker);

        final Document doc = Document.builder()
                .id("doc1")
                .rawContent("fallback content for indexing")
                .field("title", "")
                .field("body", "   ")
                .build();

        indexer.index(doc);

        final List<SearchResult> results = searcher.searchDetailed("fallback");
        assertFalse(results.isEmpty(),
                "Expected rawContent fallback when all field values are blank");
        assertEquals("doc1", results.getFirst().document().id());
    }
}
