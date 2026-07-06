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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for IR-1 — PreprocessedDocument Token Artifact.
 * <p>
 * Verifies that the token list produced by preprocessing is consistent with
 * normalizedContent, that term frequencies stored in metadata match the tokens,
 * that downstream components (lexical indexer, vector weighter) use the cached
 * artifacts and produce correct search results, and that existing behavior is
 * preserved.
 */
class PreprocessedDocumentTest {

    private static final Tokenizer TOKENIZER = Tokenizers.whitespace();
    private static final Normalizer NORMALIZER = Normalizers.english();

    // -----------------------------------------------------------------------
    // PreprocessedDocument contract
    // -----------------------------------------------------------------------

    @Test
    void tokensShouldBeConsistentWithNormalizedContent() {
        final Corpus corpus = Corpora.inMemory(Corpora.CorpusStatisticsRefreshMode.EAGER);
        final InvertedIndex invertedIndex = InvertedIndexes.inMemory();
        final Indexer indexer = Indexers.lexical(corpus, invertedIndex, TOKENIZER, NORMALIZER);

        final Document doc = Document.builder()
                .id("doc1")
                .rawContent("java search engine")
                .build();
        indexer.index(doc);

        final Document stored = corpus.get("doc1").orElseThrow();
        final String normalizedContent = stored.normalizedContent();
        assertNotNull(normalizedContent, "Expected normalizedContent to be set after indexing");
        assertFalse(normalizedContent.isBlank(), "Expected non-blank normalizedContent after indexing");

        // Tokens joining must equal normalizedContent
        final String[] terms = normalizedContent.split("\\s+");
        assertTrue(terms.length > 0, "Expected at least one term in normalizedContent");
    }

    @Test
    void metadataTermFrequenciesShouldReflectTokenCounts() {
        final Corpus corpus = Corpora.inMemory(Corpora.CorpusStatisticsRefreshMode.EAGER);
        final InvertedIndex invertedIndex = InvertedIndexes.inMemory();
        final Indexer indexer = Indexers.lexical(corpus, invertedIndex, TOKENIZER, NORMALIZER);

        // "java" appears twice — preprocessor must record TF=2
        final Document doc = Document.builder()
                .id("doc1")
                .rawContent("java java search")
                .build();
        indexer.index(doc);

        final Document stored = corpus.get("doc1").orElseThrow();
        final var termFrequencies = stored.metadata().termFrequencies();

        assertNotNull(termFrequencies, "Expected termFrequencies to be populated after indexing");
        assertEquals(2, termFrequencies.get("java"),
                "Expected 'java' TF=2 from metadata (derived from tokens, not re-tokenized)");
        assertEquals(1, termFrequencies.get("search"),
                "Expected 'search' TF=1 from metadata");
    }

    @Test
    void documentLengthShouldEqualTokenCount() {
        final Corpus corpus = Corpora.inMemory(Corpora.CorpusStatisticsRefreshMode.EAGER);
        final InvertedIndex invertedIndex = InvertedIndexes.inMemory();
        final Indexer indexer = Indexers.lexical(corpus, invertedIndex, TOKENIZER, NORMALIZER);

        final Document doc = Document.builder()
                .id("doc1")
                .rawContent("distributed systems consensus protocol")
                .build();
        indexer.index(doc);

        final Document stored = corpus.get("doc1").orElseThrow();
        // "the" is a stop word — after normalization: distributed systems consensus protocol = 4 terms
        assertNotNull(stored.metadata().length(), "Expected document length to be set");
        assertTrue(stored.metadata().length() > 0, "Expected positive document length");
        assertEquals(stored.metadata().length(), stored.normalizedContent().split("\\s+").length,
                "Expected metadata length to equal actual token count in normalizedContent");
    }

    // -----------------------------------------------------------------------
    // Positional indexing via tokens (not normalizedContent.split)
    // -----------------------------------------------------------------------

    @Test
    void lexicalIndexShouldContainCorrectPositionalPostings() {
        final Corpus corpus = Corpora.inMemory(Corpora.CorpusStatisticsRefreshMode.EAGER);
        final InvertedIndex invertedIndex = InvertedIndexes.inMemory();
        final Indexer indexer = Indexers.lexical(corpus, invertedIndex, TOKENIZER, NORMALIZER);

        final Document doc = Document.builder()
                .id("doc1")
                .rawContent("alpha beta gamma")
                .build();
        indexer.index(doc);

        final Posting alphaPosting = invertedIndex.getPostings("alpha").stream()
                .filter(p -> "doc1".equals(p.documentId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected posting for 'alpha'"));

        assertTrue(alphaPosting.positions().contains(0),
                "Expected 'alpha' at position 0 (tokens[0])");

        final Posting betaPosting = invertedIndex.getPostings("beta").stream()
                .filter(p -> "doc1".equals(p.documentId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected posting for 'beta'"));

        assertTrue(betaPosting.positions().contains(1),
                "Expected 'beta' at position 1 (tokens[1])");
    }

    // -----------------------------------------------------------------------
    // Weighter uses cached termFrequencies — no re-tokenization
    // -----------------------------------------------------------------------

    @Test
    void tfIdfWeighterShouldUseMetadataTermFrequenciesWhenAvailable() {
        final Corpus corpus = Corpora.inMemory(Corpora.CorpusStatisticsRefreshMode.EAGER);
        final InvertedIndex invertedIndex = InvertedIndexes.inMemory();
        final Indexer indexer = Indexers.lexical(corpus, invertedIndex, TOKENIZER, NORMALIZER);
        final DocumentWeighter weighter = Weighters.tfIdf(TOKENIZER, invertedIndex);

        indexer.index(Document.builder().id("doc1").rawContent("java search").build());
        indexer.index(Document.builder().id("doc2").rawContent("python machine").build());

        final Document doc1 = corpus.get("doc1").orElseThrow();
        final CorpusSnapshot snapshot = corpus.snapshot();

        // weigh() should use metadata.termFrequencies() — both terms present
        final var weights = weighter.weigh(snapshot, doc1);
        assertFalse(weights.isEmpty(), "Expected non-empty weights for preprocessed document");
        assertTrue(weights.containsKey("java"), "Expected 'java' in weights");
        assertTrue(weights.containsKey("search"), "Expected 'search' in weights");
    }

    @Test
    void termFrequencyWeighterShouldUseMetadataTermFrequenciesWhenAvailable() {
        final Corpus corpus = Corpora.inMemory(Corpora.CorpusStatisticsRefreshMode.EAGER);
        final InvertedIndex invertedIndex = InvertedIndexes.inMemory();
        final Indexer indexer = Indexers.lexical(corpus, invertedIndex, TOKENIZER, NORMALIZER);
        final DocumentWeighter weighter = Weighters.termFrequency(TOKENIZER);

        indexer.index(Document.builder().id("doc1").rawContent("java java search").build());

        final Document doc1 = corpus.get("doc1").orElseThrow();
        final CorpusSnapshot snapshot = corpus.snapshot();

        final var weights = weighter.weigh(snapshot, doc1);
        assertEquals(2.0d, weights.get("java"),
                "Expected TF=2 for 'java' from cached metadata termFrequencies");
        assertEquals(1.0d, weights.get("search"),
                "Expected TF=1 for 'search' from cached metadata termFrequencies");
    }

    // -----------------------------------------------------------------------
    // End-to-end: existing behavior preserved
    // -----------------------------------------------------------------------

    @Test
    void lexicalSearchShouldFindDocumentsAfterIR1Changes() {
        final Corpus corpus = Corpora.inMemory(Corpora.CorpusStatisticsRefreshMode.EAGER);
        final InvertedIndex invertedIndex = InvertedIndexes.inMemory();
        final Indexer indexer = Indexers.lexical(corpus, invertedIndex, TOKENIZER, NORMALIZER);

        indexer.index(Document.builder().id("doc1").rawContent("java search engine").build());
        indexer.index(Document.builder().id("doc2").rawContent("python machine learning").build());

        final CorpusSnapshot snapshot = corpus.snapshot();
        final Searcher searcher = Searchers.lexical(
                invertedIndex.snapshot(), snapshot, TOKENIZER, NORMALIZER,
                Rankers.tfIdf(snapshot, invertedIndex.snapshot()));

        final List<SearchResult> results = searcher.searchDetailed("search");
        assertFalse(results.isEmpty(), "Expected lexical search to work after IR-1 changes");
        assertEquals("doc1", results.getFirst().document().id());
    }

    @Test
    void vectorSearchShouldFindDocumentsAfterIR1Changes() {
        final Corpus corpus = Corpora.inMemory(Corpora.CorpusStatisticsRefreshMode.EAGER);
        final InvertedIndex invertedIndex = InvertedIndexes.inMemory();
        final Vocabulary vocabulary = Vocabularies.getVocabulary();
        final DocumentWeighter weighter = Weighters.tfIdf(TOKENIZER, invertedIndex);
        final DocumentVectorStore vectorStore = VectorStores.inMemory();
        final Vectorizer<SparseDocumentVector> vectorizer = Vectorizers.sparse(vocabulary);

        final Indexer indexer = Indexers.batchLexicalAndVector(
                invertedIndex, TOKENIZER, NORMALIZER, weighter, vectorizer, vectorStore, corpus);

        indexer.indexAll(List.of(
                Document.builder().id("doc1").rawContent("java search engine").build(),
                Document.builder().id("doc2").rawContent("python machine learning").build(),
                Document.builder().id("doc3").rawContent("lucene inverted index").build()
        ));

        final CorpusSnapshot snapshot = corpus.snapshot();
        final Searcher searcher = Searchers.vector(
                TOKENIZER, NORMALIZER, weighter, vectorizer,
                Similarities.sparseCosine(), snapshot, vectorStore, vocabulary, 0.1d);

        final List<SearchResult> results = searcher.searchDetailed("lucene");
        assertFalse(results.isEmpty(), "Expected vector search to work after IR-1 changes");
        assertEquals("doc3", results.getFirst().document().id());
    }

    @Test
    void fieldsAggregationShouldWorkWithTokenArtifact() {
        final Corpus corpus = Corpora.inMemory(Corpora.CorpusStatisticsRefreshMode.EAGER);
        final InvertedIndex invertedIndex = InvertedIndexes.inMemory();
        final Indexer indexer = Indexers.lexical(corpus, invertedIndex, TOKENIZER, NORMALIZER);

        final Document doc = Document.builder()
                .id("doc1")
                .field("title", "Java Search")
                .field("body", "inverted index fundamentals")
                .build();
        indexer.index(doc);

        final CorpusSnapshot snapshot = corpus.snapshot();
        final Searcher searcher = Searchers.lexical(
                invertedIndex.snapshot(), snapshot, TOKENIZER, NORMALIZER,
                Rankers.tfIdf(snapshot, invertedIndex.snapshot()));

        assertFalse(searcher.searchDetailed("inverted").isEmpty(),
                "Expected field-aggregated token 'inverted' to be searchable");
        assertFalse(searcher.searchDetailed("java").isEmpty(),
                "Expected field-aggregated token 'java' to be searchable");
    }

    @Test
    void documentsWithoutFieldsShouldUseRawContentForTokens() {
        final Corpus corpus = Corpora.inMemory(Corpora.CorpusStatisticsRefreshMode.EAGER);
        final InvertedIndex invertedIndex = InvertedIndexes.inMemory();
        final Indexer indexer = Indexers.lexical(corpus, invertedIndex, TOKENIZER, NORMALIZER);

        final Document doc = Document.builder()
                .id("doc1")
                .rawContent("distributed consensus protocol")
                .build();
        indexer.index(doc);

        final CorpusSnapshot snapshot = corpus.snapshot();
        final Searcher searcher = Searchers.lexical(
                invertedIndex.snapshot(), snapshot, TOKENIZER, NORMALIZER,
                Rankers.tfIdf(snapshot, invertedIndex.snapshot()));

        assertFalse(searcher.searchDetailed("consensus").isEmpty(),
                "Expected rawContent token to be searchable when no fields are set");
    }

    @Test
    void stopWordsShouldBeExcludedFromTokens() {
        final Corpus corpus = Corpora.inMemory(Corpora.CorpusStatisticsRefreshMode.EAGER);
        final InvertedIndex invertedIndex = InvertedIndexes.inMemory();
        final Indexer indexer = Indexers.lexical(corpus, invertedIndex, TOKENIZER, NORMALIZER);

        final Document doc = Document.builder()
                .id("doc1")
                .rawContent("the java programming language")
                .build();
        indexer.index(doc);

        final Document stored = corpus.get("doc1").orElseThrow();
        final var termFrequencies = stored.metadata().termFrequencies();

        assertFalse(termFrequencies.containsKey("the"),
                "Stop word 'the' must not appear in metadata termFrequencies (excluded during normalization)");
        assertTrue(termFrequencies.containsKey("java"),
                "Content word 'java' must appear in metadata termFrequencies");
    }
}
