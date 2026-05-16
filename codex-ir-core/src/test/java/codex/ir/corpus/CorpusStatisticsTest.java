package codex.ir.corpus;

import codex.ir.Document;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CorpusStatisticsTest {

    // --- CorpusStatistics.empty() ---

    @Test
    void emptyStatisticsStartAtVersionZero() {
        final CorpusStatistics stats = CorpusStatistics.empty();

        assertEquals(0L, stats.version());
        assertEquals(0, stats.documentCount());
        assertEquals(0L, stats.totalDocumentLength());
        assertEquals(0, stats.documentsWithLength());
        assertEquals(0.0, stats.averageDocumentLength());
    }

    // --- CorpusStatistics.snapshot() ---

    @Test
    void snapshotComputesAverageDocumentLengthCorrectly() {
        final CorpusStatistics stats = CorpusStatistics.snapshot(1L, 2, 300L, 2);

        assertEquals(150.0, stats.averageDocumentLength());
    }

    @Test
    void snapshotAvoidsDivisionByZeroWhenNoDocumentsWithLength() {
        final CorpusStatistics stats = CorpusStatistics.snapshot(1L, 0, 0L, 0);

        assertEquals(0.0, stats.averageDocumentLength());
    }

    // --- InMemoryCorpus version increment ---

    @Test
    void addingDocumentIncrementsStatisticsVersion() {
        final Corpus corpus = Corpora.inMemory(Corpora.CorpusStatisticsRefreshMode.EAGER);
        final long initialVersion = corpus.statistics().version();

        corpus.add(documentWithLength("doc-1", 100));

        assertEquals(initialVersion + 1, corpus.statistics().version());
    }

    @Test
    void addingDocumentUpdatesDocumentCount() {
        final Corpus corpus = Corpora.inMemory(Corpora.CorpusStatisticsRefreshMode.EAGER);

        corpus.add(documentWithLength("doc-1", 100));

        assertEquals(1, corpus.statistics().documentCount());
    }

    @Test
    void addingDocumentUpdatesAverageDocumentLength() {
        final Corpus corpus = Corpora.inMemory(Corpora.CorpusStatisticsRefreshMode.EAGER);

        corpus.add(documentWithLength("doc-1", 100));
        corpus.add(documentWithLength("doc-2", 200));

        assertEquals(150.0, corpus.statistics().averageDocumentLength());
    }

    @Test
    void replacingDocumentIncrementsStatisticsVersion() {
        final Corpus corpus = Corpora.inMemory(Corpora.CorpusStatisticsRefreshMode.EAGER);
        corpus.add(documentWithLength("doc-1", 100));
        final long versionAfterFirstAdd = corpus.statistics().version();

        corpus.add(documentWithLength("doc-1", 200));

        assertEquals(versionAfterFirstAdd + 1, corpus.statistics().version());
    }

    @Test
    void replacingDocumentKeepsDocumentCountAtOne() {
        final Corpus corpus = Corpora.inMemory(Corpora.CorpusStatisticsRefreshMode.EAGER);
        corpus.add(documentWithLength("doc-1", 100));

        corpus.add(documentWithLength("doc-1", 200));

        assertEquals(1, corpus.statistics().documentCount());
    }

    @Test
    void replacingDocumentUpdatesTotalDocumentLength() {
        final Corpus corpus = Corpora.inMemory(Corpora.CorpusStatisticsRefreshMode.EAGER);
        corpus.add(documentWithLength("doc-1", 100));

        corpus.add(documentWithLength("doc-1", 200));

        assertEquals(200L, corpus.statistics().totalDocumentLength());
        assertEquals(200.0, corpus.statistics().averageDocumentLength());
    }

    @Test
    void emptyCorpusHasAverageDocumentLengthOfZero() {
        final Corpus corpus = Corpora.inMemory(Corpora.CorpusStatisticsRefreshMode.EAGER);

        assertEquals(0.0, corpus.statistics().averageDocumentLength());
    }

    @Test
    void defaultInMemoryReturnsEagerStatisticsImmediatelyAfterAdd() {
        final Corpus corpus = Corpora.inMemory();

        corpus.add(documentWithLength("doc-1", 100));

        assertEquals(1, corpus.statistics().documentCount(),
                "Expected default inMemory() corpus to return up-to-date documentCount immediately after add");
        assertEquals(100L, corpus.statistics().totalDocumentLength(),
                "Expected default inMemory() corpus to return up-to-date totalDocumentLength immediately after add");
    }

    // --- CorpusStatistics.from() ---

    @Test
    void fromShouldReturnEmptyStatsForEmptyCorpus() {
        final Corpus corpus = Corpora.inMemory();

        final CorpusStatistics stats = CorpusStatistics.from(corpus, 0L);

        assertEquals(0, stats.documentCount());
        assertEquals(0L, stats.totalDocumentLength());
        assertEquals(0, stats.documentsWithLength());
        assertEquals(0.0, stats.averageDocumentLength());
    }

    @Test
    void fromShouldCountOneDocumentWithLength() {
        final Corpus corpus = Corpora.inMemory();
        corpus.add(documentWithLength("doc-1", 100));

        final CorpusStatistics stats = CorpusStatistics.from(corpus, 1L);

        assertEquals(1, stats.documentCount());
        assertEquals(100L, stats.totalDocumentLength());
        assertEquals(1, stats.documentsWithLength());
        assertEquals(100.0, stats.averageDocumentLength());
    }

    @Test
    void fromShouldComputeAverageForMultipleDocumentsWithLength() {
        final Corpus corpus = Corpora.inMemory();
        corpus.add(documentWithLength("doc-1", 100));
        corpus.add(documentWithLength("doc-2", 200));
        corpus.add(documentWithLength("doc-3", 300));

        final CorpusStatistics stats = CorpusStatistics.from(corpus, 1L);

        assertEquals(3, stats.documentCount());
        assertEquals(600L, stats.totalDocumentLength());
        assertEquals(3, stats.documentsWithLength());
        assertEquals(200.0, stats.averageDocumentLength());
    }

    @Test
    void fromShouldExcludeNullLengthDocumentsFromLengthCalculations() {
        final Corpus corpus = Corpora.inMemory();
        corpus.add(documentWithLength("doc-1", 100));
        corpus.add(documentWithoutLength("doc-no-length"));
        corpus.add(documentWithLength("doc-2", 300));

        final CorpusStatistics stats = CorpusStatistics.from(corpus, 1L);

        assertEquals(3, stats.documentCount(),
                "Expected documentCount to include all documents, even those without length");
        assertEquals(400L, stats.totalDocumentLength(),
                "Expected totalDocumentLength to exclude documents with null length");
        assertEquals(2, stats.documentsWithLength(),
                "Expected documentsWithLength to count only documents with non-null length");
        assertEquals(200.0, stats.averageDocumentLength(),
                "Expected averageDocumentLength to be computed from length-bearing documents only");
    }

    @Test
    void fromShouldHandleZeroLengthDocument() {
        final Corpus corpus = Corpora.inMemory();
        corpus.add(documentWithLength("zero-doc", 0));
        corpus.add(documentWithLength("doc-2", 200));

        final CorpusStatistics stats = CorpusStatistics.from(corpus, 1L);

        assertEquals(2, stats.documentCount());
        assertEquals(200L, stats.totalDocumentLength());
        assertEquals(2, stats.documentsWithLength(),
                "Expected documentsWithLength to include zero-length documents "
                + "(length=0 is non-null, so the document contributes)");
        assertEquals(100.0, stats.averageDocumentLength());
    }

    @Test
    void fromShouldReflectReplacementViaAddWithSameId() {
        final Corpus corpus = Corpora.inMemory();
        corpus.add(documentWithLength("doc-1", 100));
        corpus.add(documentWithLength("doc-1", 500));

        final CorpusStatistics stats = CorpusStatistics.from(corpus, 1L);

        assertEquals(1, stats.documentCount(),
                "Expected documentCount to be 1 after replacement");
        assertEquals(500L, stats.totalDocumentLength(),
                "Expected totalDocumentLength to reflect the replacement document's length");
        assertEquals(1, stats.documentsWithLength());
        assertEquals(500.0, stats.averageDocumentLength());
    }

    @Test
    void fromShouldBeConsistentWithInMemoryCorpusStatistics() {
        final Corpus corpus = Corpora.inMemory(Corpora.CorpusStatisticsRefreshMode.EAGER);
        corpus.add(documentWithLength("doc-1", 100));
        corpus.add(documentWithLength("doc-2", 250));

        final CorpusStatistics fromStats = CorpusStatistics.from(corpus, 1L);
        final CorpusStatistics incrStats = corpus.statistics();

        assertEquals(incrStats.documentCount(), fromStats.documentCount(),
                "Expected same documentCount from scan and incremental paths");
        assertEquals(incrStats.totalDocumentLength(), fromStats.totalDocumentLength(),
                "Expected same totalDocumentLength from scan and incremental paths");
        assertEquals(incrStats.documentsWithLength(), fromStats.documentsWithLength(),
                "Expected same documentsWithLength from scan and incremental paths");
        assertEquals(incrStats.averageDocumentLength(), fromStats.averageDocumentLength(),
                "Expected same averageDocumentLength from scan and incremental paths");
    }

    @Test
    void fromShouldHandleCorpusWithOnlyNullLengthDocuments() {
        final Corpus corpus = Corpora.inMemory();
        corpus.add(documentWithoutLength("doc-1"));
        corpus.add(documentWithoutLength("doc-2"));

        final CorpusStatistics stats = CorpusStatistics.from(corpus, 1L);

        assertEquals(2, stats.documentCount(),
                "Expected documentCount to count all documents");
        assertEquals(0L, stats.totalDocumentLength(),
                "Expected totalDocumentLength to be 0 when no documents have length");
        assertEquals(0, stats.documentsWithLength(),
                "Expected documentsWithLength to be 0 when no documents have length");
        assertEquals(0.0, stats.averageDocumentLength(),
                "Expected averageDocumentLength to be 0 when no documents have length");
    }

    @Test
    void fromShouldHandleMixOfNullAndZeroLengthDocuments() {
        final Corpus corpus = Corpora.inMemory();
        corpus.add(documentWithoutLength("doc-null"));
        corpus.add(documentWithLength("doc-zero", 0));
        corpus.add(documentWithLength("doc-normal", 150));

        final CorpusStatistics stats = CorpusStatistics.from(corpus, 1L);

        assertEquals(3, stats.documentCount());
        assertEquals(150L, stats.totalDocumentLength(),
                "Expected totalDocumentLength to sum zero and normal lengths, excluding null");
        assertEquals(2, stats.documentsWithLength(),
                "Expected documentsWithLength to include zero-length and normal doc, exclude null-length doc");
        assertEquals(75.0, stats.averageDocumentLength(),
                "Expected average = 150 / 2 = 75");
    }

    // --- helpers ---

    private static Document documentWithLength(final String id, final int length) {
        return Document.builder()
                .id(id)
                .rawContent("content")
                .normalizedContent("content")
                .length(length)
                .uniqueTerms(1)
                .build();
    }

    private static Document documentWithoutLength(final String id) {
        return Document.builder()
                .id(id)
                .rawContent("content")
                .normalizedContent("content")
                .build();
    }
}
