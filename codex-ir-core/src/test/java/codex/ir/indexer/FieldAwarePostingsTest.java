package codex.ir.indexer;

import codex.ir.Document;
import codex.ir.corpus.Corpora;
import codex.ir.corpus.Corpus;
import codex.ir.normalizer.Normalizer;
import codex.ir.normalizer.Normalizers;
import codex.ir.tokenizer.Tokenizer;
import codex.ir.tokenizer.Tokenizers;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for IR-3 — Field-Aware Postings.
 * <p>
 * Verifies that {@link Posting#fieldFrequencies()} is populated from
 * {@link FieldTokenSequence} data for structured-field documents, while
 * whole-document {@code termFrequency} and {@code positions} remain unchanged.
 */
class FieldAwarePostingsTest {

    private static final Tokenizer TOKENIZER = Tokenizers.whitespace();
    private static final Normalizer NORMALIZER = Normalizers.english();

    // -----------------------------------------------------------------------
    // fieldFrequencies populated for field-structured documents
    // -----------------------------------------------------------------------

    @Test
    void singleFieldDocumentShouldHaveFieldFrequencyForIndexedTerm() {
        final Corpus corpus = Corpora.inMemory(Corpora.CorpusStatisticsRefreshMode.EAGER);
        final InvertedIndex index = InvertedIndexes.inMemory();
        final Indexer indexer = Indexers.lexical(corpus, index, TOKENIZER, NORMALIZER);

        indexer.index(Document.builder()
                .id("doc1")
                .field("title", "java search")
                .build());

        final Posting posting = postingFor(index, "java", "doc1");
        assertNotNull(posting, "Expected posting for 'java'");
        assertEquals(1, posting.fieldFrequencies().get("title"),
                "Expected field frequency of 1 for 'java' in 'title' field");
    }

    @Test
    void twoFieldDocumentShouldCarryFrequenciesForBothFields() {
        final Corpus corpus = Corpora.inMemory(Corpora.CorpusStatisticsRefreshMode.EAGER);
        final InvertedIndex index = InvertedIndexes.inMemory();
        final Indexer indexer = Indexers.lexical(corpus, index, TOKENIZER, NORMALIZER);

        indexer.index(Document.builder()
                .id("doc1")
                .field("title", "java guide")
                .field("body", "java search engine")
                .build());

        final Posting javaPosting = postingFor(index, "java", "doc1");
        assertNotNull(javaPosting);

        // whole-document TF must be 2 (one from each field)
        assertEquals(2, javaPosting.termFrequency(),
                "Expected whole-document TF=2 for 'java' appearing in title and body");

        // per-field: 1 each
        assertEquals(1, javaPosting.fieldFrequencies().get("title"),
                "Expected title field frequency = 1 for 'java'");
        assertEquals(1, javaPosting.fieldFrequencies().get("body"),
                "Expected body field frequency = 1 for 'java'");
    }

    @Test
    void termAppearedTwiceInSameFieldShouldHaveFieldFrequencyTwo() {
        final Corpus corpus = Corpora.inMemory(Corpora.CorpusStatisticsRefreshMode.EAGER);
        final InvertedIndex index = InvertedIndexes.inMemory();
        final Indexer indexer = Indexers.lexical(corpus, index, TOKENIZER, NORMALIZER);

        indexer.index(Document.builder()
                .id("doc1")
                .field("body", "java portable java language")
                .build());

        final Posting posting = postingFor(index, "java", "doc1");
        assertNotNull(posting);
        assertEquals(2, posting.termFrequency(),
                "Expected whole-document TF=2 for 'java' appearing twice in body");
        assertEquals(2, posting.fieldFrequencies().get("body"),
                "Expected body field frequency=2 for 'java' appearing twice");
    }

    @Test
    void termOnlyInOneFieldShouldNotAppearInOtherFieldFrequency() {
        final Corpus corpus = Corpora.inMemory(Corpora.CorpusStatisticsRefreshMode.EAGER);
        final InvertedIndex index = InvertedIndexes.inMemory();
        final Indexer indexer = Indexers.lexical(corpus, index, TOKENIZER, NORMALIZER);

        indexer.index(Document.builder()
                .id("doc1")
                .field("title", "distributed consensus")
                .field("body", "raft protocol implementation")
                .build());

        final Posting consensusPosting = postingFor(index, "consensus", "doc1");
        assertNotNull(consensusPosting);
        assertEquals(1, consensusPosting.fieldFrequencies().get("title"),
                "Expected 'consensus' frequency = 1 in title");
        assertTrue(consensusPosting.fieldFrequencies().getOrDefault("body", 0) == 0,
                "Expected 'consensus' to have no frequency in body");
    }

    // -----------------------------------------------------------------------
    // Raw-content documents have empty fieldFrequencies
    // -----------------------------------------------------------------------

    @Test
    void rawContentDocumentShouldHaveEmptyFieldFrequencies() {
        final Corpus corpus = Corpora.inMemory(Corpora.CorpusStatisticsRefreshMode.EAGER);
        final InvertedIndex index = InvertedIndexes.inMemory();
        final Indexer indexer = Indexers.lexical(corpus, index, TOKENIZER, NORMALIZER);

        indexer.index(Document.builder()
                .id("doc1")
                .rawContent("java search engine")
                .build());

        final Posting posting = postingFor(index, "java", "doc1");
        assertNotNull(posting);
        assertTrue(posting.fieldFrequencies().isEmpty(),
                "Expected empty fieldFrequencies for raw-content document");
    }

    @Test
    void allBlankFieldsFallbackDocumentShouldHaveEmptyFieldFrequencies() {
        final Corpus corpus = Corpora.inMemory(Corpora.CorpusStatisticsRefreshMode.EAGER);
        final InvertedIndex index = InvertedIndexes.inMemory();
        final Indexer indexer = Indexers.lexical(corpus, index, TOKENIZER, NORMALIZER);

        indexer.index(Document.builder()
                .id("doc1")
                .rawContent("fallback content here")
                .field("title", "")
                .field("body", "   ")
                .build());

        final Posting posting = postingFor(index, "fallback", "doc1");
        assertNotNull(posting);
        assertTrue(posting.fieldFrequencies().isEmpty(),
                "Expected empty fieldFrequencies when all fields are blank and rawContent fallback is used");
    }

    // -----------------------------------------------------------------------
    // Whole-document behavior is unchanged by field frequency tracking
    // -----------------------------------------------------------------------

    @Test
    void wholeDocumentTermFrequencyIsUnchangedByFieldTracking() {
        final Corpus corpus = Corpora.inMemory(Corpora.CorpusStatisticsRefreshMode.EAGER);
        final InvertedIndex index = InvertedIndexes.inMemory();
        final Indexer indexer = Indexers.lexical(corpus, index, TOKENIZER, NORMALIZER);

        indexer.index(Document.builder()
                .id("doc1")
                .field("title", "java")
                .field("body", "java portable language")
                .build());

        final Posting javaPosting = postingFor(index, "java", "doc1");
        assertNotNull(javaPosting);

        // whole-document TF must aggregate both field occurrences
        assertEquals(2, javaPosting.termFrequency(),
                "Whole-document termFrequency must aggregate across all fields");

        // positions list must have 2 entries (position in whole-document token stream)
        assertEquals(2, javaPosting.positions().size(),
                "Whole-document positions list size must match termFrequency");
    }

    @Test
    void fieldFrequenciesDoNotAffectPostingsForUnrelatedDocuments() {
        final Corpus corpus = Corpora.inMemory(Corpora.CorpusStatisticsRefreshMode.EAGER);
        final InvertedIndex index = InvertedIndexes.inMemory();
        final Indexer indexer = Indexers.lexical(corpus, index, TOKENIZER, NORMALIZER);

        indexer.index(Document.builder()
                .id("doc1")
                .field("title", "java guide")
                .build());
        indexer.index(Document.builder()
                .id("doc2")
                .rawContent("java tutorial")
                .build());

        // doc2 is a raw-content document — its posting for 'java' must have no field data
        final Posting doc2Posting = postingFor(index, "java", "doc2");
        assertNotNull(doc2Posting);
        assertTrue(doc2Posting.fieldFrequencies().isEmpty(),
                "Raw-content document's posting must have empty fieldFrequencies even when other docs have field data");

        // doc1's posting must have title field data
        final Posting doc1Posting = postingFor(index, "java", "doc1");
        assertNotNull(doc1Posting);
        assertEquals(1, doc1Posting.fieldFrequencies().get("title"),
                "Field-document posting must still carry field frequency data");
    }

    // -----------------------------------------------------------------------
    // Snapshot carries field frequency data
    // -----------------------------------------------------------------------

    @Test
    void indexSnapshotShouldCarryFieldFrequencies() {
        final Corpus corpus = Corpora.inMemory(Corpora.CorpusStatisticsRefreshMode.EAGER);
        final InvertedIndex index = InvertedIndexes.inMemory();
        final Indexer indexer = Indexers.lexical(corpus, index, TOKENIZER, NORMALIZER);

        // "java" is not stemmed; appears in both fields
        indexer.index(Document.builder()
                .id("doc1")
                .field("title", "java platform")
                .field("body", "java search engine")
                .build());

        final IndexSnapshot snapshot = index.snapshot();
        final Posting posting = snapshot.getPostings("java").stream()
                .filter(p -> "doc1".equals(p.documentId()))
                .findFirst()
                .orElse(null);

        assertNotNull(posting, "Expected 'java' posting in snapshot for doc1");
        assertEquals(2, posting.termFrequency(),
                "Snapshot posting must carry whole-document TF");
        assertEquals(1, posting.fieldFrequencies().get("title"),
                "Snapshot posting must carry title field frequency");
        assertEquals(1, posting.fieldFrequencies().get("body"),
                "Snapshot posting must carry body field frequency");
    }

    // -----------------------------------------------------------------------
    // Batch pipeline propagates field frequency data
    // -----------------------------------------------------------------------

    @Test
    void batchIndexerShouldPopulateFieldFrequencies() {
        final Corpus corpus = Corpora.inMemory(Corpora.CorpusStatisticsRefreshMode.EAGER);
        final InvertedIndex index = InvertedIndexes.inMemory();
        final codex.ir.corpus.vector.Vocabulary vocabulary =
                codex.ir.corpus.vector.Vocabularies.getVocabulary();
        final codex.ir.vector.Vectorizer<codex.ir.vector.SparseDocumentVector> vectorizer =
                codex.ir.vector.Vectorizers.sparse(vocabulary);
        final codex.ir.vector.store.DocumentVectorStore vectorStore =
                codex.ir.vector.store.VectorStores.inMemory();
        final codex.ir.weight.DocumentWeighter weighter =
                codex.ir.weight.Weighters.tfIdf(TOKENIZER, index);

        final Indexer indexer = Indexers.batchLexicalAndVector(
                index, TOKENIZER, NORMALIZER, weighter, vectorizer, vectorStore, corpus);

        final List<Document> docs = List.of(
                Document.builder()
                        .id("doc1")
                        .field("title", "java collections")
                        .field("body", "sorting and searching algorithms")
                        .build(),
                Document.builder()
                        .id("doc2")
                        .rawContent("python scripting language")
                        .build()
        );

        indexer.indexAll(docs);

        final Posting javaPosting = postingFor(index, "java", "doc1");
        assertNotNull(javaPosting, "Expected posting for 'java' in doc1 after batch indexing");
        assertEquals(Map.of("title", 1), javaPosting.fieldFrequencies(),
                "Expected batch-indexed field doc to carry field frequencies");

        final Posting pythonPosting = postingFor(index, "python", "doc2");
        assertNotNull(pythonPosting, "Expected posting for 'python' in doc2 after batch indexing");
        assertTrue(pythonPosting.fieldFrequencies().isEmpty(),
                "Expected raw-content doc to have empty fieldFrequencies after batch indexing");
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static Posting postingFor(final InvertedIndex index, final String term, final String docId) {
        return index.getPostings(term).stream()
                .filter(p -> docId.equals(p.documentId()))
                .findFirst()
                .orElse(null);
    }
}
