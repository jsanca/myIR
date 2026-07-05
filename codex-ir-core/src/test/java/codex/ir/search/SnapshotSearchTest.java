package codex.ir.search;

import codex.ir.Document;
import codex.ir.corpus.Corpora;
import codex.ir.corpus.Corpus;
import codex.ir.corpus.CorpusSnapshot;
import codex.ir.indexer.IndexSnapshot;
import codex.ir.indexer.InvertedIndex;
import codex.ir.indexer.InvertedIndexes;
import codex.ir.indexer.Indexer;
import codex.ir.indexer.Indexers;
import codex.ir.normalizer.Normalizer;
import codex.ir.normalizer.Normalizers;
import codex.ir.ranking.Ranker;
import codex.ir.ranking.Rankers;
import codex.ir.tokenizer.Tokenizer;
import codex.ir.tokenizer.Tokenizers;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behavioural tests for the IR-0 read/write boundary (see Plan01-Roadmap-Field-AwareIR.md).
 * A snapshot must be a frozen, point-in-time view: writes after snapshot() must not
 * affect searches performed against the earlier snapshot.
 */
class SnapshotSearchTest {

    @Test
    void snapshotShouldIsolateSearchFromSubsequentWrites() {
        final Tokenizer tokenizer = Tokenizers.whitespace();
        final Normalizer normalizer = Normalizers.english();
        final Corpus corpus = Corpora.inMemory(Corpora.CorpusStatisticsRefreshMode.EAGER);
        final InvertedIndex invertedIndex = InvertedIndexes.inMemory();
        final Indexer indexer = Indexers.lexical(corpus, invertedIndex, tokenizer, normalizer);

        final Document doc1 = Document.builder()
                .id("doc1")
                .rawContent("java search engine")
                .build();

        indexer.index(doc1);

        final CorpusSnapshot snapshot = corpus.snapshot();
        final IndexSnapshot indexSnapshot = invertedIndex.snapshot();
        final Ranker ranker = Rankers.tfIdf(snapshot, indexSnapshot);
        final Searcher searcher = Searchers.lexical(indexSnapshot, snapshot, tokenizer, normalizer, ranker);

        // Index a second document AFTER taking the snapshot
        final Document doc2 = Document.builder()
                .id("doc2")
                .rawContent("python machine learning")
                .build();
        indexer.index(doc2);

        // The snapshot-based searcher must not see doc2
        final List<SearchResult> results = searcher.searchDetailed("python");
        assertTrue(results.isEmpty(),
                "Expected snapshot-based searcher to be unaffected by writes after snapshot()");
    }

    @Test
    void freshSnapshotShouldReflectNewDocuments() {
        final Tokenizer tokenizer = Tokenizers.whitespace();
        final Normalizer normalizer = Normalizers.english();
        final Corpus corpus = Corpora.inMemory(Corpora.CorpusStatisticsRefreshMode.EAGER);
        final InvertedIndex invertedIndex = InvertedIndexes.inMemory();
        final Indexer indexer = Indexers.lexical(corpus, invertedIndex, tokenizer, normalizer);

        final Document doc1 = Document.builder()
                .id("doc1")
                .rawContent("java search engine")
                .build();

        indexer.index(doc1);

        // Add a second document then take a fresh snapshot
        final Document doc2 = Document.builder()
                .id("doc2")
                .rawContent("python machine learning")
                .build();
        indexer.index(doc2);

        final CorpusSnapshot snapshot = corpus.snapshot();
        final IndexSnapshot indexSnapshot = invertedIndex.snapshot();
        final Ranker ranker = Rankers.tfIdf(snapshot, indexSnapshot);
        final Searcher searcher = Searchers.lexical(indexSnapshot, snapshot, tokenizer, normalizer, ranker);

        final List<SearchResult> results = searcher.searchDetailed("python");
        assertFalse(results.isEmpty(),
                "Expected fresh snapshot to include all documents indexed before snapshot()");
        assertEquals("doc2", results.getFirst().document().id());
    }

    @Test
    void searchAgainstSnapshotShouldFindIndexedDocuments() {
        final Tokenizer tokenizer = Tokenizers.whitespace();
        final Normalizer normalizer = Normalizers.english();
        final Corpus corpus = Corpora.inMemory(Corpora.CorpusStatisticsRefreshMode.EAGER);
        final InvertedIndex invertedIndex = InvertedIndexes.inMemory();
        final Indexer indexer = Indexers.lexical(corpus, invertedIndex, tokenizer, normalizer);

        final Document doc = Document.builder()
                .id("doc1")
                .rawContent("distributed consensus protocol")
                .build();

        indexer.index(doc);

        final CorpusSnapshot snapshot = corpus.snapshot();
        final IndexSnapshot indexSnapshot = invertedIndex.snapshot();
        final Ranker ranker = Rankers.tfIdf(snapshot, indexSnapshot);
        final Searcher searcher = Searchers.lexical(indexSnapshot, snapshot, tokenizer, normalizer, ranker);

        final List<SearchResult> results = searcher.searchDetailed("consensus");
        assertFalse(results.isEmpty(),
                "Expected snapshot-based search to find the indexed document");
        assertEquals("doc1", results.getFirst().document().id());
    }

    @Test
    void snapshotCorpusSizeReflectsStateAtSnapshotTime() {
        final Corpus corpus = Corpora.inMemory(Corpora.CorpusStatisticsRefreshMode.EAGER);
        final InvertedIndex invertedIndex = InvertedIndexes.inMemory();
        final Tokenizer tokenizer = Tokenizers.whitespace();
        final Normalizer normalizer = Normalizers.english();
        final Indexer indexer = Indexers.lexical(corpus, invertedIndex, tokenizer, normalizer);

        indexer.index(Document.builder().id("a").rawContent("alpha").build());
        indexer.index(Document.builder().id("b").rawContent("beta").build());

        final CorpusSnapshot snapshot = corpus.snapshot();

        // Add a third document after the snapshot
        indexer.index(Document.builder().id("c").rawContent("gamma").build());

        assertEquals(2, snapshot.size(),
                "Expected snapshot size to reflect corpus state at snapshot() time, not after");
        assertEquals(3, corpus.snapshot().size(),
                "Expected fresh snapshot to reflect the updated corpus size");
    }
}
