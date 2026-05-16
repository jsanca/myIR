package codex.ir.indexer;

import codex.ir.Document;
import codex.ir.corpus.Corpora;
import codex.ir.corpus.Corpus;
import codex.ir.normalizer.Normalizer;
import codex.ir.normalizer.Normalizers;
import codex.ir.tokenizer.Tokenizer;
import codex.ir.tokenizer.Tokenizers;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentPreprocessorTest {

    private final Tokenizer tokenizer = Tokenizers.whitespace();
    private final Normalizer normalizer = Normalizers.english();

    @Test
    void shouldPreprocessWhenNormalizedContentPresentButMetadataEmpty() {
        final Corpus corpus = Corpora.inMemory();
        final InvertedIndex invertedIndex = InvertedIndexes.inMemory();
        final Indexer indexer = Indexers.lexical(corpus, invertedIndex, tokenizer, normalizer);

        final Document doc = Document.builder()
                .id("doc-1")
                .rawContent("java search engine")
                .normalizedContent("java search engine")
                .build();

        indexer.index(doc);

        final Document stored = corpus.get("doc-1").orElseThrow();
        assertNotNull(stored.metadata().length(),
                "Expected length to be set after preprocessing (metadata was empty)");
        assertEquals(3, stored.metadata().length().intValue(),
                "Expected document length to be computed from rawContent via preprocessing");
        assertNotNull(stored.metadata().uniqueTerms(),
                "Expected uniqueTerms to be set after preprocessing");
    }

    @Test
    void shouldPreprocessWhenNormalizedContentPresentButLengthIsNull() {
        final Corpus corpus = Corpora.inMemory();
        final InvertedIndex invertedIndex = InvertedIndexes.inMemory();
        final Indexer indexer = Indexers.lexical(corpus, invertedIndex, tokenizer, normalizer);

        final Document doc = Document.builder()
                .id("doc-2")
                .rawContent("java search engine")
                .normalizedContent("java search engine")
                .uniqueTerms(3)
                .termFrequencies(Map.of("java", 1, "search", 1, "engine", 1))
                .build();

        indexer.index(doc);

        final Document stored = corpus.get("doc-2").orElseThrow();
        assertNotNull(stored.metadata().length(),
                "Expected length to be populated when it was previously null");
        assertEquals(3, stored.metadata().length().intValue(),
                "Expected document length to be computed via preprocessing");
    }

    @Test
    void shouldPreprocessWhenNormalizedContentIsBlank() {
        final Corpus corpus = Corpora.inMemory();
        final InvertedIndex invertedIndex = InvertedIndexes.inMemory();
        final Indexer indexer = Indexers.lexical(corpus, invertedIndex, tokenizer, normalizer);

        final Document doc = Document.builder()
                .id("doc-3")
                .rawContent("java search engine")
                .normalizedContent("")
                .length(0)
                .uniqueTerms(0)
                .termFrequencies(Map.of())
                .build();

        indexer.index(doc);

        final Document stored = corpus.get("doc-3").orElseThrow();
        assertFalse(stored.normalizedContent().isBlank(),
                "Expected normalizedContent to be populated from rawContent "
                + "because blank normalizedContent should not be considered preprocessed");
        assertTrue(stored.metadata().length() > 0,
                "Expected length to be computed from rawContent, not reused from blank normalizedContent");
    }

    @Test
    void shouldSkipPreprocessingForFullyPreprocessedDocument() {
        final Corpus corpus = Corpora.inMemory();
        final InvertedIndex invertedIndex = InvertedIndexes.inMemory();
        final Indexer indexer = Indexers.lexical(corpus, invertedIndex, tokenizer, normalizer);

        final Document doc = Document.builder()
                .id("doc-4")
                .rawContent("original raw text")
                .normalizedContent("already preprocessed content")
                .length(3)
                .uniqueTerms(3)
                .termFrequencies(Map.of("already", 1, "preprocessed", 1, "content", 1))
                .build();

        indexer.index(doc);

        final Document stored = corpus.get("doc-4").orElseThrow();
        assertEquals("already preprocessed content", stored.normalizedContent(),
                "Expected normalizedContent to remain unchanged when document is fully preprocessed");
        assertEquals(3, stored.metadata().length().intValue(),
                "Expected length to remain unchanged when fully preprocessed");
    }

    @Test
    void shouldSkipPreprocessingWhenRawContentAndNormalizedContentBothPresentWithFullMetadata() {
        final Corpus corpus = Corpora.inMemory();
        final InvertedIndex invertedIndex = InvertedIndexes.inMemory();
        final Indexer indexer = Indexers.lexical(corpus, invertedIndex, tokenizer, normalizer);

        final Document doc = Document.builder()
                .id("doc-5")
                .rawContent("different raw text")
                .normalizedContent("canonical normalized content")
                .length(3)
                .uniqueTerms(3)
                .termFrequencies(Map.of("canonical", 1, "normalized", 1, "content", 1))
                .build();

        indexer.index(doc);

        final Document stored = corpus.get("doc-5").orElseThrow();
        assertEquals("canonical normalized content", stored.normalizedContent(),
                "Expected normalizedContent to be authoritative when document is fully preprocessed");
    }

    @Test
    void shouldSkipPreprocessingWhenFieldsAndNormalizedContentBothPresentWithFullMetadata() {
        final Corpus corpus = Corpora.inMemory();
        final InvertedIndex invertedIndex = InvertedIndexes.inMemory();
        final Indexer indexer = Indexers.lexical(corpus, invertedIndex, tokenizer, normalizer);

        final Document doc = Document.builder()
                .id("doc-6")
                .field("title", "different field text")
                .field("body", "should not be used")
                .normalizedContent("precomputed aggregated content")
                .length(3)
                .uniqueTerms(3)
                .termFrequencies(Map.of("precomputed", 1, "aggregated", 1, "content", 1))
                .build();

        indexer.index(doc);

        final Document stored = corpus.get("doc-6").orElseThrow();
        assertEquals("precomputed aggregated content", stored.normalizedContent(),
                "Expected precomputed normalizedContent to be authoritative when fully preprocessed");
    }

    @Test
    void shouldPreprocessRawDocumentWithOnlyRawContent() {
        final Corpus corpus = Corpora.inMemory();
        final InvertedIndex invertedIndex = InvertedIndexes.inMemory();
        final Indexer indexer = Indexers.lexical(corpus, invertedIndex, tokenizer, normalizer);

        final Document doc = Document.builder()
                .id("doc-7")
                .rawContent("java search engine")
                .build();

        indexer.index(doc);

        final Document stored = corpus.get("doc-7").orElseThrow();
        assertEquals("java search engine", stored.normalizedContent(),
                "Expected normalizedContent to be computed from rawContent");
        assertEquals(3, stored.metadata().length().intValue());
        assertNotNull(stored.metadata().termFrequencies());
        assertFalse(stored.metadata().termFrequencies().isEmpty());
    }

    @Test
    void shouldPreprocessRawDocumentWithOnlyFields() {
        final Corpus corpus = Corpora.inMemory();
        final InvertedIndex invertedIndex = InvertedIndexes.inMemory();
        final Indexer indexer = Indexers.lexical(corpus, invertedIndex, tokenizer, normalizer);

        final Document doc = Document.builder()
                .id("doc-8")
                .field("title", "Java Search Engine")
                .field("body", "building a search system")
                .build();

        indexer.index(doc);

        final Document stored = corpus.get("doc-8").orElseThrow();
        assertFalse(stored.normalizedContent().isBlank(),
                "Expected normalizedContent to be computed from aggregated field values");
        assertNotNull(stored.metadata().length());
        assertTrue(stored.metadata().length() > 0);
        assertNotNull(stored.metadata().termFrequencies());
        assertFalse(stored.metadata().termFrequencies().isEmpty());

        final Optional<Document> found = corpus.get("doc-8");
        assertTrue(found.isPresent());
        final String normalized = found.get().normalizedContent();
        assertTrue(normalized.contains("java"),
                "Expected field content to appear in normalizedContent after preprocessing");
        assertTrue(normalized.contains("search"),
                "Expected field content to appear in normalizedContent after preprocessing");
    }
}
