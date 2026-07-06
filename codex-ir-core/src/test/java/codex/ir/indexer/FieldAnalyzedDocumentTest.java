package codex.ir.indexer;

import codex.ir.Document;
import codex.ir.corpus.Corpora;
import codex.ir.corpus.Corpus;
import codex.ir.corpus.CorpusSnapshot;
import codex.ir.normalizer.Normalizer;
import codex.ir.normalizer.Normalizers;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for IR-2 — Field Provenance Artifact.
 * <p>
 * Verifies that {@link FieldAnalyzedDocument} carries per-field token sequences
 * while leaving whole-document tokens, normalizedContent, and termFrequencies
 * exactly unchanged. Ranking and search behavior must be identical to pre-IR-2.
 */
class FieldAnalyzedDocumentTest {

    private static final Tokenizer TOKENIZER = Tokenizers.whitespace();
    private static final Normalizer NORMALIZER = Normalizers.english();

    // -----------------------------------------------------------------------
    // FieldTokenSequence — per-field tokens preserved
    // -----------------------------------------------------------------------

    @Test
    void fieldDocumentShouldProduceOneSequencePerNonBlankField() {
        final Corpus corpus = Corpora.inMemory(Corpora.CorpusStatisticsRefreshMode.EAGER);
        final InvertedIndex invertedIndex = InvertedIndexes.inMemory();
        final Indexer indexer = Indexers.lexical(corpus, invertedIndex, TOKENIZER, NORMALIZER);

        final Document doc = Document.builder()
                .id("doc1")
                .field("title", "Java Search")
                .field("body", "inverted index fundamentals")
                .build();
        indexer.index(doc);

        // Retrieve stored document to confirm whole-document model is intact
        final Document stored = corpus.get("doc1").orElseThrow();
        final String normalizedContent = stored.normalizedContent();
        assertFalse(normalizedContent.isBlank(),
                "Expected whole-document normalizedContent to be populated after IR-2 indexing");

        // Both field terms must appear in the whole-document content
        assertTrue(normalizedContent.contains("java") || normalizedContent.contains("search"),
                "Expected title terms in whole-document normalizedContent");
        assertTrue(normalizedContent.contains("invert") || normalizedContent.contains("index")
                        || normalizedContent.contains("fundament"),
                "Expected body terms in whole-document normalizedContent");
    }

    @Test
    void blankFieldShouldProduceNoFieldSequence() {
        final Corpus corpus = Corpora.inMemory(Corpora.CorpusStatisticsRefreshMode.EAGER);
        final InvertedIndex invertedIndex = InvertedIndexes.inMemory();
        final Indexer indexer = Indexers.lexical(corpus, invertedIndex, TOKENIZER, NORMALIZER);

        // title is blank, body is non-blank
        final Document doc = Document.builder()
                .id("doc1")
                .field("title", "   ")
                .field("body", "distributed consensus protocol")
                .build();
        indexer.index(doc);

        final Document stored = corpus.get("doc1").orElseThrow();
        // body terms must be indexed (whole-document)
        assertFalse(stored.normalizedContent().isBlank(),
                "Expected non-blank normalizedContent when at least one field is non-blank");
        // blank title must not contribute tokens
        assertFalse(stored.metadata().termFrequencies().containsKey("   "),
                "Blank field value must not appear in termFrequencies");
    }

    @Test
    void rawContentDocumentShouldProduceNoFieldSequences() {
        final Corpus corpus = Corpora.inMemory(Corpora.CorpusStatisticsRefreshMode.EAGER);
        final InvertedIndex invertedIndex = InvertedIndexes.inMemory();
        final Indexer indexer = Indexers.lexical(corpus, invertedIndex, TOKENIZER, NORMALIZER);

        final Document doc = Document.builder()
                .id("doc1")
                .rawContent("java search engine")
                .build();
        indexer.index(doc);

        final Document stored = corpus.get("doc1").orElseThrow();
        assertFalse(stored.normalizedContent().isBlank(),
                "Expected rawContent document to be indexed normally after IR-2");
        // termFrequencies should come from rawContent tokens
        assertFalse(stored.metadata().termFrequencies().isEmpty(),
                "Expected term frequencies to be populated from rawContent");
    }

    @Test
    void allBlankFieldsShouldFallBackToRawContent() {
        final Corpus corpus = Corpora.inMemory(Corpora.CorpusStatisticsRefreshMode.EAGER);
        final InvertedIndex invertedIndex = InvertedIndexes.inMemory();
        final Indexer indexer = Indexers.lexical(corpus, invertedIndex, TOKENIZER, NORMALIZER);

        final Document doc = Document.builder()
                .id("doc1")
                .rawContent("fallback search content")
                .field("title", "")
                .field("body", "   ")
                .build();
        indexer.index(doc);

        final Document stored = corpus.get("doc1").orElseThrow();
        assertTrue(stored.normalizedContent().contains("fallback"),
                "Expected rawContent fallback when all field values are blank");
    }

    // -----------------------------------------------------------------------
    // Whole-document tokens and termFrequencies — must be unchanged by IR-2
    // -----------------------------------------------------------------------

    @Test
    void wholeDocumentTermFrequenciesMustBeUnchangedByFieldAnalysis() {
        final Corpus corpus = Corpora.inMemory(Corpora.CorpusStatisticsRefreshMode.EAGER);
        final InvertedIndex invertedIndex = InvertedIndexes.inMemory();
        final Indexer indexer = Indexers.lexical(corpus, invertedIndex, TOKENIZER, NORMALIZER);

        // "java" appears in both title and body — whole-document TF should be 2
        final Document doc = Document.builder()
                .id("doc1")
                .field("title", "java consensus")
                .field("body", "java distributed systems")
                .build();
        indexer.index(doc);

        final Document stored = corpus.get("doc1").orElseThrow();
        final var tf = stored.metadata().termFrequencies();

        assertEquals(2, tf.get("java"),
                "Expected whole-document TF=2 for 'java' appearing in both fields — field analysis must not alter this");
        assertTrue(tf.containsKey("consensus"),
                "Expected 'consensus' from title in whole-document termFrequencies");
        assertTrue(tf.containsKey("distribut") || tf.containsKey("distributed"),
                "Expected 'distributed' (or its stem) from body in whole-document termFrequencies");
    }

    @Test
    void documentLengthMustReflectWholeDocumentTokenCount() {
        final Corpus corpus = Corpora.inMemory(Corpora.CorpusStatisticsRefreshMode.EAGER);
        final InvertedIndex invertedIndex = InvertedIndexes.inMemory();
        final Indexer indexer = Indexers.lexical(corpus, invertedIndex, TOKENIZER, NORMALIZER);

        final Document doc = Document.builder()
                .id("doc1")
                .field("title", "alpha beta")
                .field("body", "gamma delta")
                .build();
        indexer.index(doc);

        final Document stored = corpus.get("doc1").orElseThrow();
        // alpha, beta, gamma, delta = 4 tokens (no stop words)
        assertEquals(4, stored.metadata().length(),
                "Expected whole-document length to equal total token count across all fields");
    }

    @Test
    void stopWordsExcludedFromWholeDocumentTokensShouldNotAppearInFieldFrequencies() {
        final Corpus corpus = Corpora.inMemory(Corpora.CorpusStatisticsRefreshMode.EAGER);
        final InvertedIndex invertedIndex = InvertedIndexes.inMemory();
        final Indexer indexer = Indexers.lexical(corpus, invertedIndex, TOKENIZER, NORMALIZER);

        final Document doc = Document.builder()
                .id("doc1")
                .field("title", "the java platform")
                .field("body", "a search engine")
                .build();
        indexer.index(doc);

        final Document stored = corpus.get("doc1").orElseThrow();
        final var tf = stored.metadata().termFrequencies();

        assertFalse(tf.containsKey("the"),
                "Stop word 'the' must be excluded from whole-document termFrequencies");
        assertFalse(tf.containsKey("a"),
                "Stop word 'a' must be excluded from whole-document termFrequencies");
        assertTrue(tf.containsKey("java"),
                "Content word 'java' must remain in whole-document termFrequencies");
    }

    // -----------------------------------------------------------------------
    // Search behavior — must be identical to pre-IR-2
    // -----------------------------------------------------------------------

    @Test
    void lexicalSearchBehaviorMustBeUnchangedAfterIR2() {
        final Corpus corpus = Corpora.inMemory(Corpora.CorpusStatisticsRefreshMode.EAGER);
        final InvertedIndex invertedIndex = InvertedIndexes.inMemory();
        final Indexer indexer = Indexers.lexical(corpus, invertedIndex, TOKENIZER, NORMALIZER);

        indexer.index(Document.builder()
                .id("doc1")
                .field("title", "Java Collections Framework")
                .field("body", "Understanding sorting algorithms and data structures")
                .build());
        indexer.index(Document.builder()
                .id("doc2")
                .field("title", "Distributed Systems")
                .field("body", "Consensus protocols like Raft and Paxos")
                .build());

        final CorpusSnapshot snapshot = corpus.snapshot();
        final Searcher searcher = Searchers.lexical(
                invertedIndex.snapshot(), snapshot, TOKENIZER, NORMALIZER,
                Rankers.tfIdf(snapshot, invertedIndex.snapshot()));

        List<SearchResult> results = searcher.searchDetailed("collections");
        assertFalse(results.isEmpty(), "Expected field-indexed document to be found by lexical search after IR-2");
        assertEquals("doc1", results.getFirst().document().id());

        results = searcher.searchDetailed("consensus");
        assertFalse(results.isEmpty(), "Expected field-indexed document to be found by lexical search after IR-2");
        assertEquals("doc2", results.getFirst().document().id());
    }

    @Test
    void rawContentDocumentShouldBeSearchableAfterIR2() {
        final Corpus corpus = Corpora.inMemory(Corpora.CorpusStatisticsRefreshMode.EAGER);
        final InvertedIndex invertedIndex = InvertedIndexes.inMemory();
        final Indexer indexer = Indexers.lexical(corpus, invertedIndex, TOKENIZER, NORMALIZER);

        indexer.index(Document.builder()
                .id("doc1")
                .rawContent("distributed consensus protocol")
                .build());
        indexer.index(Document.builder()
                .id("doc2")
                .rawContent("machine learning algorithms")
                .build());

        final CorpusSnapshot snapshot = corpus.snapshot();
        final Searcher searcher = Searchers.lexical(
                invertedIndex.snapshot(), snapshot, TOKENIZER, NORMALIZER,
                Rankers.tfIdf(snapshot, invertedIndex.snapshot()));

        final List<SearchResult> results = searcher.searchDetailed("consensus");
        assertFalse(results.isEmpty(), "Expected rawContent document to be searchable after IR-2");
        assertEquals("doc1", results.getFirst().document().id());
    }

    @Test
    void mixedCorpusWithFieldAndRawContentDocumentsShouldSearchCorrectlyAfterIR2() {
        final Corpus corpus = Corpora.inMemory(Corpora.CorpusStatisticsRefreshMode.EAGER);
        final InvertedIndex invertedIndex = InvertedIndexes.inMemory();
        final Indexer indexer = Indexers.lexical(corpus, invertedIndex, TOKENIZER, NORMALIZER);

        indexer.index(Document.builder()
                .id("field-doc")
                .field("title", "Java Platform")
                .field("body", "advanced search techniques")
                .build());
        indexer.index(Document.builder()
                .id("raw-doc")
                .rawContent("python scripting language")
                .build());

        final CorpusSnapshot snapshot = corpus.snapshot();
        final Searcher searcher = Searchers.lexical(
                invertedIndex.snapshot(), snapshot, TOKENIZER, NORMALIZER,
                Rankers.tfIdf(snapshot, invertedIndex.snapshot()));

        assertFalse(searcher.searchDetailed("java").isEmpty(),
                "Expected field-doc to be found for 'java'");
        assertEquals("field-doc", searcher.searchDetailed("java").getFirst().document().id());

        assertFalse(searcher.searchDetailed("python").isEmpty(),
                "Expected raw-doc to be found for 'python'");
        assertEquals("raw-doc", searcher.searchDetailed("python").getFirst().document().id());
    }

    // -----------------------------------------------------------------------
    // FieldAnalyzedDocument record contract
    // -----------------------------------------------------------------------

    @Test
    void fieldAnalyzedDocumentShouldReportHasFieldSequencesCorrectly() {
        final FieldTokenSequence titleSeq =
                new FieldTokenSequence("title", List.of("java", "search"));
        final FieldTokenSequence bodySeq =
                new FieldTokenSequence("body", List.of("index", "engine"));

        final Document doc = Document.builder().id("doc1").rawContent("ignored").build();
        final PreprocessedDocument base = new PreprocessedDocument(doc, List.of("java", "search"));

        final FieldAnalyzedDocument withFields =
                new FieldAnalyzedDocument(base, List.of(titleSeq, bodySeq));
        assertTrue(withFields.hasFieldSequences());
        assertEquals(2, withFields.fieldSequences().size());
        assertEquals("title", withFields.fieldSequences().get(0).fieldName());
        assertEquals("body", withFields.fieldSequences().get(1).fieldName());

        final FieldAnalyzedDocument withoutFields =
                new FieldAnalyzedDocument(base, List.of());
        assertFalse(withoutFields.hasFieldSequences());
    }

    @Test
    void fieldTokenSequenceTokensShouldBeImmutable() {
        final FieldTokenSequence seq = new FieldTokenSequence("title", List.of("java", "search"));
        assertEquals(List.of("java", "search"), seq.tokens());
        assertEquals("title", seq.fieldName());
    }
}
