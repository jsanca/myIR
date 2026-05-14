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
}
