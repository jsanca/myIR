package codex.ir.indexer;

import codex.ir.Document;
import codex.ir.corpus.Corpora;
import codex.ir.corpus.Corpus;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests verifying the current whole-document field aggregation
 * contract (see ADR-004). Fields are aggregated into a single
 * normalizedContent during preprocessing. Per-field indexing is not yet
 * supported.
 * <p>
 * Tests that use TF-IDF vector search pre-seed a background document because
 * the incremental {@code lexicalAndVector} indexer computes IDF from the
 * corpus state at indexing time. A single-document corpus produces IDF=0
 * for all terms, making vector search infeasible on the first document.
 */
class FieldAwareVectorIndexingTest {

    @Test
    void lexicalAndVectorShouldIndexFieldDocumentsForBothSearchPaths() {
        final Tokenizer tokenizer = Tokenizers.whitespace();
        final Normalizer normalizer = Normalizers.english();
        final Corpus corpus = Corpora.inMemory();
        final InvertedIndex invertedIndex = InvertedIndexes.inMemory();
        final DocumentWeighter documentWeighter = Weighters.tfIdf(tokenizer, invertedIndex);
        final Vocabulary vocabulary = Vocabularies.getVocabulary();
        final Vectorizer<SparseDocumentVector> vectorizer = Vectorizers.sparse(vocabulary);
        final DocumentVectorStore documentVectorStore = VectorStores.inMemory();

        final Document seedDoc = Document.builder()
                .id("seed-doc")
                .rawContent("background seed document for corpus statistics")
                .build();

        final Indexer lexicalIndexer = Indexers.lexical(corpus, invertedIndex, tokenizer, normalizer);
        lexicalIndexer.index(seedDoc);

        final Indexer indexer = Indexers.lexicalAndVector(
                invertedIndex, tokenizer, normalizer,
                documentWeighter, vectorizer, documentVectorStore, corpus);

        final Document doc1 = Document.builder()
                .id("doc1")
                .field("title", "Java Collections Framework")
                .field("body", "Understanding sorting algorithms and tree data structures")
                .build();

        final Document doc2 = Document.builder()
                .id("doc2")
                .field("title", "Distributed Systems")
                .field("body", "Consensus protocols like Raft and Paxos")
                .build();

        indexer.index(doc1);
        indexer.index(doc2);

        final Searcher lexicalSearcher = Searchers.lexical(
                invertedIndex.snapshot(), corpus.snapshot(), tokenizer, normalizer,
                Rankers.tfIdf(corpus.snapshot(), invertedIndex.snapshot()));

        final Searcher vectorSearcher = Searchers.vector(
                tokenizer, normalizer, documentWeighter, vectorizer,
                Similarities.sparseCosine(), corpus.snapshot(), documentVectorStore, vocabulary, 0.1d);

        List<SearchResult> lexicalResults = lexicalSearcher.searchDetailed("collections");
        assertFalse(lexicalResults.isEmpty(),
                "Expected lexical search to find term from title field");
        assertEquals("doc1", lexicalResults.getFirst().document().id());

        lexicalResults = lexicalSearcher.searchDetailed("algorithms");
        assertFalse(lexicalResults.isEmpty(),
                "Expected lexical search to find term from body field");
        assertEquals("doc1", lexicalResults.getFirst().document().id());

        final List<SearchResult> vectorResults = vectorSearcher.searchDetailed("consensus");
        assertFalse(vectorResults.isEmpty(),
                "Expected vector search to find term from body field in field-aware document");
        assertEquals("doc2", vectorResults.getFirst().document().id());

        final Optional<Document> stored = corpus.get("doc1");
        assertTrue(stored.isPresent(), "Expected corpus to contain the indexed document");
        assertFalse(stored.get().normalizedContent().isBlank(),
                "Expected preprocessed document to have normalizedContent from aggregated field values");
    }

    @Test
    void vectorSearchShouldFindDocumentsWithOnlyFieldsAndNoRawContent() {
        final Tokenizer tokenizer = Tokenizers.whitespace();
        final Normalizer normalizer = Normalizers.english();
        final Corpus corpus = Corpora.inMemory();
        final InvertedIndex invertedIndex = InvertedIndexes.inMemory();
        final DocumentWeighter documentWeighter = Weighters.tfIdf(tokenizer, invertedIndex);
        final Vocabulary vocabulary = Vocabularies.getVocabulary();
        final Vectorizer<SparseDocumentVector> vectorizer = Vectorizers.sparse(vocabulary);
        final DocumentVectorStore documentVectorStore = VectorStores.inMemory();

        final Document seedDoc = Document.builder()
                .id("seed-doc")
                .rawContent("background seed document for corpus statistics")
                .build();

        final Indexer lexicalIndexer = Indexers.lexical(corpus, invertedIndex, tokenizer, normalizer);
        lexicalIndexer.index(seedDoc);

        final Indexer indexer = Indexers.lexicalAndVector(
                invertedIndex, tokenizer, normalizer,
                documentWeighter, vectorizer, documentVectorStore, corpus);

        final Document doc1 = Document.builder()
                .id("fields-only-doc-1")
                .field("title", "Machine Learning Basics")
                .field("body", "Supervised and unsupervised learning techniques")
                .build();

        final Document doc2 = Document.builder()
                .id("fields-only-doc-2")
                .field("title", "Distributed Consensus Protocols")
                .field("body", "Raft and Paxos are core algorithms in distributed computing")
                .build();

        indexer.index(doc1);
        indexer.index(doc2);

        final Searcher vectorSearcher = Searchers.vector(
                tokenizer, normalizer, documentWeighter, vectorizer,
                Similarities.sparseCosine(), corpus.snapshot(), documentVectorStore, vocabulary, 0.1d);

        final List<SearchResult> consensusResults = vectorSearcher.searchDetailed("consensus");
        assertFalse(consensusResults.isEmpty(),
                "Expected vector search to find term from title field in fields-only document");
        assertEquals("fields-only-doc-2", consensusResults.getFirst().document().id());

        final List<SearchResult> algorithmsResults = vectorSearcher.searchDetailed("algorithms");
        assertFalse(algorithmsResults.isEmpty(),
                "Expected vector search to find term from body field in fields-only document");
        assertEquals("fields-only-doc-2", algorithmsResults.getFirst().document().id());
    }

    @Test
    void duplicateTermAcrossFieldsShouldAggregateTermFrequency() {
        final Tokenizer tokenizer = Tokenizers.whitespace();
        final Normalizer normalizer = Normalizers.english();
        final Corpus corpus = Corpora.inMemory();
        final InvertedIndex invertedIndex = InvertedIndexes.inMemory();
        final DocumentWeighter documentWeighter = Weighters.tfIdf(tokenizer, invertedIndex);
        final Vocabulary vocabulary = Vocabularies.getVocabulary();
        final Vectorizer<SparseDocumentVector> vectorizer = Vectorizers.sparse(vocabulary);
        final DocumentVectorStore documentVectorStore = VectorStores.inMemory();

        final Document seedDoc = Document.builder()
                .id("seed-doc")
                .rawContent("background seed document for corpus statistics")
                .build();

        final Indexer lexicalIndexer = Indexers.lexical(corpus, invertedIndex, tokenizer, normalizer);
        lexicalIndexer.index(seedDoc);

        final Indexer indexer = Indexers.lexicalAndVector(
                invertedIndex, tokenizer, normalizer,
                documentWeighter, vectorizer, documentVectorStore, corpus);

        final Document doc = Document.builder()
                .id("dup-doc")
                .field("title", "java")
                .field("body", "java portable language")
                .build();

        final Document anotherDoc = Document.builder()
                .id("another-doc")
                .field("title", "rust systems")
                .field("body", "rust for safe concurrent programming")
                .build();

        indexer.index(doc);
        indexer.index(anotherDoc);

        final Posting javaPosting = invertedIndex.getPostings("java")
                .stream()
                .filter(p -> "dup-doc".equals(p.documentId()))
                .findFirst()
                .orElseThrow();

        assertEquals(2, javaPosting.termFrequency(),
                "Expected 'java' term frequency to be 2 when it appears in both title and body fields");

        final Searcher vectorSearcher = Searchers.vector(
                tokenizer, normalizer, documentWeighter, vectorizer,
                Similarities.sparseCosine(), corpus.snapshot(), documentVectorStore, vocabulary, 0.1d);

        final List<SearchResult> javaResults = vectorSearcher.searchDetailed("java");
        assertFalse(javaResults.isEmpty(),
                "Expected vector search to find the document containing 'java' in both fields");
        assertEquals("dup-doc", javaResults.getFirst().document().id());

        final List<SearchResult> portableResults = vectorSearcher.searchDetailed("portable");
        assertFalse(portableResults.isEmpty(),
                "Expected vector search to find body-only term in the same document");
    }

    @Test
    void fieldsShouldTakePrecedenceOverRawContent() {
        final Tokenizer tokenizer = Tokenizers.whitespace();
        final Normalizer normalizer = Normalizers.english();
        final Corpus corpus = Corpora.inMemory();
        final InvertedIndex invertedIndex = InvertedIndexes.inMemory();

        final Indexer indexer = Indexers.lexical(corpus, invertedIndex, tokenizer, normalizer);

        final Document doc = Document.builder()
                .id("precedence-doc")
                .rawContent("legacy classic content")
                .field("title", "modern article")
                .build();

        indexer.index(doc);

        final Searcher searcher = Searchers.lexical(
                invertedIndex.snapshot(), corpus.snapshot(), tokenizer, normalizer,
                Rankers.tfIdf(corpus.snapshot(), invertedIndex.snapshot()));

        final List<SearchResult> legacyResults = searcher.searchDetailed("legacy");
        assertTrue(legacyResults.isEmpty(),
                "Expected rawContent term 'legacy' to be ignored when fields are present");

        final List<SearchResult> modernResults = searcher.searchDetailed("modern");
        assertFalse(modernResults.isEmpty(),
                "Expected field content term 'modern' to be indexed when fields are present");
        assertEquals("precedence-doc", modernResults.getFirst().document().id());

        final List<SearchResult> articleResults = searcher.searchDetailed("article");
        assertFalse(articleResults.isEmpty(),
                "Expected field content term 'article' to be indexed when fields are present");
        assertEquals("precedence-doc", articleResults.getFirst().document().id());
    }

    @Test
    void mixedCorpusWithFieldDocumentsAndRawContentDocumentsShouldSearchCorrectly() {
        final Tokenizer tokenizer = Tokenizers.whitespace();
        final Normalizer normalizer = Normalizers.english();
        final Corpus corpus = Corpora.inMemory();
        final InvertedIndex invertedIndex = InvertedIndexes.inMemory();
        final DocumentWeighter documentWeighter = Weighters.tfIdf(tokenizer, invertedIndex);
        final Vocabulary vocabulary = Vocabularies.getVocabulary();
        final Vectorizer<SparseDocumentVector> vectorizer = Vectorizers.sparse(vocabulary);
        final DocumentVectorStore documentVectorStore = VectorStores.inMemory();

        final Document seedDoc = Document.builder()
                .id("seed-doc")
                .rawContent("background seed document for corpus statistics")
                .build();

        final Indexer lexicalIndexer = Indexers.lexical(corpus, invertedIndex, tokenizer, normalizer);
        lexicalIndexer.index(seedDoc);

        final Indexer indexer = Indexers.lexicalAndVector(
                invertedIndex, tokenizer, normalizer,
                documentWeighter, vectorizer, documentVectorStore, corpus);

        final Document fieldDoc = Document.builder()
                .id("java-doc")
                .field("title", "java basics")
                .field("body", "introductory java tutorial")
                .build();

        final Document rawContentDoc = Document.builder()
                .id("python-doc")
                .rawContent("python programming language")
                .build();

        final Document anotherFieldDoc = Document.builder()
                .id("scala-doc")
                .field("title", "scala guide")
                .field("body", "functional programming with scala")
                .build();

        indexer.index(fieldDoc);
        indexer.index(rawContentDoc);
        indexer.index(anotherFieldDoc);

        final Searcher lexicalSearcher = Searchers.lexical(
                invertedIndex.snapshot(), corpus.snapshot(), tokenizer, normalizer,
                Rankers.tfIdf(corpus.snapshot(), invertedIndex.snapshot()));

        final Searcher vectorSearcher = Searchers.vector(
                tokenizer, normalizer, documentWeighter, vectorizer,
                Similarities.sparseCosine(), corpus.snapshot(), documentVectorStore, vocabulary, 0.1d);

        List<SearchResult> results = lexicalSearcher.searchDetailed("java");
        assertEquals(1, results.size(),
                "Expected lexical search for 'java' to find only the field-based java document");
        assertEquals("java-doc", results.getFirst().document().id());

        results = lexicalSearcher.searchDetailed("python");
        assertEquals(1, results.size(),
                "Expected lexical search for 'python' to find only the rawContent-based python document");
        assertEquals("python-doc", results.getFirst().document().id());

        results = lexicalSearcher.searchDetailed("scala");
        assertEquals(1, results.size(),
                "Expected lexical search for 'scala' to find only the field-based scala document");
        assertEquals("scala-doc", results.getFirst().document().id());

        results = vectorSearcher.searchDetailed("tutorial");
        assertEquals(1, results.size(),
                "Expected vector search for 'tutorial' to find only the field-based java document");
        assertEquals("java-doc", results.getFirst().document().id());

        results = vectorSearcher.searchDetailed("programming");
        assertEquals(2, results.size(),
                "Expected vector search for 'programming' to find both the python and scala documents");
    }

    @Test
    void lexicalAndVectorShouldUseNormalizedContentForBothIndexes() {
        final Tokenizer tokenizer = Tokenizers.whitespace();
        final Normalizer normalizer = Normalizers.english();
        final Corpus corpus = Corpora.inMemory();
        final InvertedIndex invertedIndex = InvertedIndexes.inMemory();
        final DocumentWeighter documentWeighter = Weighters.tfIdf(tokenizer, invertedIndex);
        final Vocabulary vocabulary = Vocabularies.getVocabulary();
        final Vectorizer<SparseDocumentVector> vectorizer = Vectorizers.sparse(vocabulary);
        final DocumentVectorStore documentVectorStore = VectorStores.inMemory();

        final Document seedDoc = Document.builder()
                .id("seed-doc")
                .rawContent("background seed document for corpus statistics")
                .build();

        final Indexer lexicalIndexer = Indexers.lexical(corpus, invertedIndex, tokenizer, normalizer);
        lexicalIndexer.index(seedDoc);

        final Indexer indexer = Indexers.lexicalAndVector(
                invertedIndex, tokenizer, normalizer,
                documentWeighter, vectorizer, documentVectorStore, corpus);

        final Document doc = Document.builder()
                .id("mixed-case-doc")
                .field("title", "THE Java Platform")
                .field("body", "The guide covers search engines")
                .build();

        final Document otherDoc = Document.builder()
                .id("other-doc")
                .field("title", "another document")
                .field("body", "for corpus statistics diversity")
                .build();

        indexer.index(doc);
        indexer.index(otherDoc);

        final Searcher lexicalSearcher = Searchers.lexical(
                invertedIndex.snapshot(), corpus.snapshot(), tokenizer, normalizer,
                Rankers.tfIdf(corpus.snapshot(), invertedIndex.snapshot()));

        final Searcher vectorSearcher = Searchers.vector(
                tokenizer, normalizer, documentWeighter, vectorizer,
                Similarities.sparseCosine(), corpus.snapshot(), documentVectorStore, vocabulary, 0.1d);

        List<SearchResult> results = lexicalSearcher.searchDetailed("java");
        assertFalse(results.isEmpty(),
                "Expected lexical search to find lowercased term from title field");
        assertEquals("mixed-case-doc", results.getFirst().document().id());

        results = lexicalSearcher.searchDetailed("platform");
        assertFalse(results.isEmpty(),
                "Expected lexical search to find lowercased term from title field");
        assertEquals("mixed-case-doc", results.getFirst().document().id());

        results = lexicalSearcher.searchDetailed("guide");
        assertFalse(results.isEmpty(),
                "Expected lexical search to find term from body field");
        assertEquals("mixed-case-doc", results.getFirst().document().id());

        results = lexicalSearcher.searchDetailed("the");
        assertTrue(results.isEmpty(),
                "Expected lexical search for stop word 'the' to return no results");

        results = lexicalSearcher.searchDetailed("THE");
        assertTrue(results.isEmpty(),
                "Expected lexical search for uppercase stop word 'THE' to return no results");

        results = vectorSearcher.searchDetailed("engines");
        assertFalse(results.isEmpty(),
                "Expected vector search to find term from body field via normalizedContent");
        assertEquals("mixed-case-doc", results.getFirst().document().id());

        results = vectorSearcher.searchDetailed("platform");
        assertFalse(results.isEmpty(),
                "Expected vector search to find lowercased term from title field via normalizedContent");
        assertEquals("mixed-case-doc", results.getFirst().document().id());

        results = vectorSearcher.searchDetailed("the");
        assertTrue(results.isEmpty(),
                "Expected vector search for stop word 'the' to return no results "
                + "(would fail if vector indexing used raw field content instead of normalizedContent)");
    }

    @Test
    void vectorIndexingMustUsePreprocessedDocumentNotOriginal() {
        final Tokenizer tokenizer = Tokenizers.whitespace();
        final Normalizer normalizer = Normalizers.english();
        final Corpus corpus = Corpora.inMemory();
        final InvertedIndex invertedIndex = InvertedIndexes.inMemory();
        final DocumentWeighter documentWeighter = Weighters.tfIdf(tokenizer, invertedIndex);
        final Vocabulary vocabulary = Vocabularies.getVocabulary();
        final Vectorizer<SparseDocumentVector> vectorizer = Vectorizers.sparse(vocabulary);
        final DocumentVectorStore documentVectorStore = VectorStores.inMemory();

        final Document seedDoc = Document.builder()
                .id("seed-doc")
                .rawContent("background seed document for corpus statistics")
                .build();

        final Indexer lexicalIndexer = Indexers.lexical(corpus, invertedIndex, tokenizer, normalizer);
        lexicalIndexer.index(seedDoc);

        final Indexer indexer = Indexers.lexicalAndVector(
                invertedIndex, tokenizer, normalizer,
                documentWeighter, vectorizer, documentVectorStore, corpus);

        final Document doc = Document.builder()
                .id("lifecycle-doc")
                .rawContent("THE legacy old content here")
                .field("title", "modern techniques today")
                .build();

        final Document otherDoc = Document.builder()
                .id("other-doc")
                .rawContent("separate background content")
                .build();

        indexer.index(doc);
        indexer.index(otherDoc);

        final Searcher vectorSearcher = Searchers.vector(
                tokenizer, normalizer, documentWeighter, vectorizer,
                Similarities.sparseCosine(), corpus.snapshot(), documentVectorStore, vocabulary, 0.1d);

        List<SearchResult> results = vectorSearcher.searchDetailed("modern");
        assertFalse(results.isEmpty(),
                "Expected vector search to find field term 'modern' from preprocessed document");
        assertEquals("lifecycle-doc", results.getFirst().document().id());

        results = vectorSearcher.searchDetailed("techniques");
        assertFalse(results.isEmpty(),
                "Expected vector search to find field term 'techniques' from preprocessed document");
        assertEquals("lifecycle-doc", results.getFirst().document().id());

        results = vectorSearcher.searchDetailed("legacy");
        assertTrue(results.isEmpty(),
                "Expected vector search for rawContent term 'legacy' to return no results "
                + "(would fail if vector indexing used original raw document instead of preprocessed document)");

        results = vectorSearcher.searchDetailed("the");
        assertTrue(results.isEmpty(),
                "Expected vector search for stop word 'the' from rawContent to return no results "
                + "(would fail if vector indexing used original raw document instead of preprocessed document)");
    }
}
