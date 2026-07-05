package codex.ir.indexer;

import codex.ir.Document;
import codex.ir.corpus.Corpora;
import codex.ir.corpus.Corpus;
import codex.ir.corpus.CorpusSnapshot;
import codex.ir.corpus.vector.Vocabularies;
import codex.ir.corpus.vector.Vocabulary;
import codex.ir.normalizer.Normalizer;
import codex.ir.normalizer.Normalizers;
import codex.ir.ranking.Rankers;
import codex.ir.search.SearchResult;
import codex.ir.search.Searcher;
import codex.ir.search.Searchers;
import codex.ir.tokenizer.Tokenizer;
import codex.ir.tokenizer.Tokenizers;
import codex.ir.vector.Similarities;
import codex.ir.vector.SparseDocumentVector;
import codex.ir.vector.Vectorizer;
import codex.ir.vector.Vectorizers;
import codex.ir.vector.store.DocumentVectorStore;
import codex.ir.vector.store.VectorStores;
import codex.ir.weight.DocumentWeighter;
import codex.ir.weight.Weighters;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for IR-0.5 — Batch Index Build Pipeline.
 * <p>
 * Validates that {@link Indexers#batchLexicalAndVector} produces correct lexical
 * and vector search results, that the single-document incremental path remains
 * unaffected, and that an empty batch is handled gracefully.
 */
class BatchIndexerTest {

    private static final Tokenizer TOKENIZER = Tokenizers.whitespace();
    private static final Normalizer NORMALIZER = Normalizers.english();

    // -----------------------------------------------------------------------
    // Batch path — lexical search
    // -----------------------------------------------------------------------

    @Test
    void batchIndexAllShouldSupportLexicalSearch() {
        final Corpus corpus = Corpora.inMemory(Corpora.CorpusStatisticsRefreshMode.EAGER);
        final InvertedIndex invertedIndex = InvertedIndexes.inMemory();
        final Vocabulary vocabulary = Vocabularies.getVocabulary();
        final DocumentWeighter weighter = Weighters.tfIdf(TOKENIZER, invertedIndex);
        final DocumentVectorStore vectorStore = VectorStores.inMemory();

        final Indexer indexer = Indexers.batchLexicalAndVector(
                invertedIndex, TOKENIZER, NORMALIZER, weighter,
                Vectorizers.sparse(vocabulary), vectorStore, corpus);

        indexer.indexAll(List.of(
                document("doc1", "java search engine"),
                document("doc2", "python machine learning"),
                document("doc3", "distributed consensus protocol")
        ));

        final CorpusSnapshot snapshot = corpus.snapshot();
        final Searcher searcher = Searchers.lexical(
                invertedIndex.snapshot(), snapshot, TOKENIZER, NORMALIZER,
                Rankers.tfIdf(snapshot, invertedIndex.snapshot()));

        final List<SearchResult> results = searcher.searchDetailed("consensus");
        assertFalse(results.isEmpty(), "Expected lexical search to find document after batch indexAll");
        assertEquals("doc3", results.getFirst().document().id());
    }

    @Test
    void batchIndexAllShouldSupportVectorSearch() {
        final Corpus corpus = Corpora.inMemory(Corpora.CorpusStatisticsRefreshMode.EAGER);
        final InvertedIndex invertedIndex = InvertedIndexes.inMemory();
        final Vocabulary vocabulary = Vocabularies.getVocabulary();
        final DocumentWeighter weighter = Weighters.tfIdf(TOKENIZER, invertedIndex);
        final DocumentVectorStore vectorStore = VectorStores.inMemory();

        final Indexer indexer = Indexers.batchLexicalAndVector(
                invertedIndex, TOKENIZER, NORMALIZER, weighter,
                Vectorizers.sparse(vocabulary), vectorStore, corpus);

        indexer.indexAll(List.of(
                document("doc1", "java search engine"),
                document("doc2", "python machine learning"),
                document("doc3", "lucene inverted index")
        ));

        final CorpusSnapshot snapshot = corpus.snapshot();
        final Searcher searcher = Searchers.vector(
                TOKENIZER, NORMALIZER, weighter,
                Vectorizers.sparse(vocabulary), Similarities.sparseCosine(),
                snapshot, vectorStore, vocabulary, 0.1d);

        final List<SearchResult> results = searcher.searchDetailed("lucene");
        assertFalse(results.isEmpty(), "Expected vector search to find document after batch indexAll");
        assertEquals("doc3", results.getFirst().document().id());
    }

    @Test
    void batchIndexAllShouldIndexAllDocumentsInTheCorpus() {
        final Corpus corpus = Corpora.inMemory(Corpora.CorpusStatisticsRefreshMode.EAGER);
        final InvertedIndex invertedIndex = InvertedIndexes.inMemory();
        final Vocabulary vocabulary = Vocabularies.getVocabulary();
        final DocumentWeighter weighter = Weighters.tfIdf(TOKENIZER, invertedIndex);
        final DocumentVectorStore vectorStore = VectorStores.inMemory();

        final Indexer indexer = Indexers.batchLexicalAndVector(
                invertedIndex, TOKENIZER, NORMALIZER, weighter,
                Vectorizers.sparse(vocabulary), vectorStore, corpus);

        indexer.indexAll(List.of(
                document("doc1", "alpha content"),
                document("doc2", "beta content"),
                document("doc3", "gamma content")
        ));

        final CorpusSnapshot snapshot = corpus.snapshot();
        assertEquals(3, snapshot.size(),
                "Expected all batch documents to appear in the corpus snapshot");
        assertTrue(snapshot.contains("doc1"));
        assertTrue(snapshot.contains("doc2"));
        assertTrue(snapshot.contains("doc3"));
    }

    // -----------------------------------------------------------------------
    // Batch path — snapshot is shared (behavioral verification)
    // -----------------------------------------------------------------------

    @Test
    void batchIndexAllShouldUseFullCorpusStatisticsForVectorWeighting() {
        // When batch-indexed together, all 3 documents contribute to IDF before
        // any vector is built. "java" appears in doc1 and doc2 (DF=2) while
        // "lucene" appears only in doc3 (DF=1). The rare term "lucene" should
        // rank first for a "lucene" query because its IDF is higher.
        final Corpus corpus = Corpora.inMemory(Corpora.CorpusStatisticsRefreshMode.EAGER);
        final InvertedIndex invertedIndex = InvertedIndexes.inMemory();
        final Vocabulary vocabulary = Vocabularies.getVocabulary();
        final DocumentWeighter weighter = Weighters.tfIdf(TOKENIZER, invertedIndex);
        final DocumentVectorStore vectorStore = VectorStores.inMemory();

        final Indexer indexer = Indexers.batchLexicalAndVector(
                invertedIndex, TOKENIZER, NORMALIZER, weighter,
                Vectorizers.sparse(vocabulary), vectorStore, corpus);

        indexer.indexAll(List.of(
                document("doc1", "java search engine"),
                document("doc2", "java programming language"),
                document("doc3", "lucene inverted index search")
        ));

        final CorpusSnapshot snapshot = corpus.snapshot();
        final Searcher searcher = Searchers.vector(
                TOKENIZER, NORMALIZER, weighter,
                Vectorizers.sparse(vocabulary), Similarities.sparseCosine(),
                snapshot, vectorStore, vocabulary, 0.1d);

        final List<SearchResult> results = searcher.searchDetailed("lucene");
        assertFalse(results.isEmpty(), "Expected vector search to return results");
        assertEquals("doc3", results.getFirst().document().id(),
                "Expected doc3 containing the rare term 'lucene' to rank first");
    }

    // -----------------------------------------------------------------------
    // Incremental path — single-document index() still works
    // -----------------------------------------------------------------------

    @Test
    void singleDocumentIndexShouldStillWorkAfterBatchIndexer() {
        final Corpus corpus = Corpora.inMemory(Corpora.CorpusStatisticsRefreshMode.EAGER);
        final InvertedIndex invertedIndex = InvertedIndexes.inMemory();
        final Vocabulary vocabulary = Vocabularies.getVocabulary();
        final DocumentWeighter weighter = Weighters.tfIdf(TOKENIZER, invertedIndex);
        final DocumentVectorStore vectorStore = VectorStores.inMemory();

        final Indexer indexer = Indexers.batchLexicalAndVector(
                invertedIndex, TOKENIZER, NORMALIZER, weighter,
                Vectorizers.sparse(vocabulary), vectorStore, corpus);

        // Use incremental single-doc API
        indexer.index(document("doc1", "java search engine"));
        indexer.index(document("doc2", "distributed systems"));

        final CorpusSnapshot snapshot = corpus.snapshot();
        final Searcher searcher = Searchers.lexical(
                invertedIndex.snapshot(), snapshot, TOKENIZER, NORMALIZER,
                Rankers.tfIdf(snapshot, invertedIndex.snapshot()));

        assertFalse(searcher.searchDetailed("distributed").isEmpty(),
                "Expected incremental index() to work via batchLexicalAndVector indexer");
        assertFalse(searcher.searchDetailed("java").isEmpty(),
                "Expected incremental index() to work via batchLexicalAndVector indexer");
    }

    // -----------------------------------------------------------------------
    // Edge cases
    // -----------------------------------------------------------------------

    @Test
    void emptyBatchShouldBeHandledGracefully() {
        final Corpus corpus = Corpora.inMemory(Corpora.CorpusStatisticsRefreshMode.EAGER);
        final InvertedIndex invertedIndex = InvertedIndexes.inMemory();
        final Vocabulary vocabulary = Vocabularies.getVocabulary();
        final DocumentWeighter weighter = Weighters.tfIdf(TOKENIZER, invertedIndex);
        final DocumentVectorStore vectorStore = VectorStores.inMemory();

        final Indexer indexer = Indexers.batchLexicalAndVector(
                invertedIndex, TOKENIZER, NORMALIZER, weighter,
                Vectorizers.sparse(vocabulary), vectorStore, corpus);

        indexer.indexAll(List.of());

        assertEquals(0, corpus.snapshot().size(),
                "Expected corpus to remain empty after indexing an empty batch");
    }

    @Test
    void defaultIndexAllOnLexicalIndexerShouldDelegateToIndex() {
        final Corpus corpus = Corpora.inMemory(Corpora.CorpusStatisticsRefreshMode.EAGER);
        final InvertedIndex invertedIndex = InvertedIndexes.inMemory();
        final Indexer indexer = Indexers.lexical(corpus, invertedIndex, TOKENIZER, NORMALIZER);

        // default indexAll() falls back to sequential index()
        indexer.indexAll(List.of(
                document("doc1", "alpha content"),
                document("doc2", "beta content")
        ));

        final CorpusSnapshot snapshot = corpus.snapshot();
        assertEquals(2, snapshot.size(),
                "Expected default indexAll to index all documents via sequential index()");
        assertTrue(snapshot.contains("doc1"));
        assertTrue(snapshot.contains("doc2"));
    }

    // -----------------------------------------------------------------------
    // Helper
    // -----------------------------------------------------------------------

    private static Document document(final String id, final String text) {
        return Document.builder()
                .id(id)
                .rawContent(text)
                .build();
    }
}
