package codex.ir.search;

import codex.ir.Document;
import codex.ir.corpus.Corpora;
import codex.ir.corpus.Corpus;
import codex.ir.corpus.vector.Vocabularies;
import codex.ir.corpus.vector.Vocabulary;
import codex.ir.indexer.InvertedIndex;
import codex.ir.indexer.InvertedIndexes;
import codex.ir.indexer.Indexer;
import codex.ir.indexer.Indexers;
import codex.ir.normalizer.Normalizer;
import codex.ir.normalizer.Normalizers;
import codex.ir.vector.Similarities;
import codex.ir.tokenizer.Tokenizer;
import codex.ir.tokenizer.Tokenizers;
import codex.ir.vector.store.DocumentVectorStore;
import codex.ir.vector.store.DocumentVectorStores;
import codex.ir.vector.SparseDocumentVector;
import codex.ir.vector.Vectorizer;
import codex.ir.vector.Vectorizers;
import codex.ir.vector.store.VectorStores;
import codex.ir.weight.DocumentWeighter;
import codex.ir.weight.Weighters;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class VectorSearcherTest {

    @Test
    void searchShouldRankDocumentContainingRareTermFirst() {
        final Tokenizer tokenizer = Tokenizers.whitespace();
        final Normalizer normalizer = Normalizers.english();
        final Vocabulary vocabulary = Vocabularies.getVocabulary();
        final Corpus corpus = Corpora.inMemory(Corpora.CorpusStatisticsRefreshMode.EAGER);
        final InvertedIndex invertedIndex = InvertedIndexes.inMemory();
        final Indexer indexer = Indexers.lexical(corpus, invertedIndex, tokenizer, normalizer);
        final DocumentWeighter documentWeighter = Weighters.tfIdf(tokenizer, invertedIndex);
        final Vectorizer<SparseDocumentVector> vectorizer = Vectorizers.sparse(vocabulary);
        final DocumentVectorStore documentVectorStore = VectorStores.inMemory();

        final Document doc1 = document("doc1", "java search engine");
        final Document doc2 = document("doc2", "java programming language");
        final Document doc3 = document("doc3", "lucene inverted index search");

        indexer.index(doc1);
        indexer.index(doc2);
        indexer.index(doc3);

        storeVector(corpus, documentWeighter, vectorizer, documentVectorStore, doc1);
        storeVector(corpus, documentWeighter, vectorizer, documentVectorStore, doc2);
        storeVector(corpus, documentWeighter, vectorizer, documentVectorStore, doc3);

        final Searcher searcher = Searchers.vector(
                tokenizer,
                normalizer,
                documentWeighter,
                vectorizer,
                Similarities.cosine(),
                corpus,
                documentVectorStore
        );

        final List<SearchResult> results = searcher.search("lucene");

        assertFalse(results.isEmpty(), "Expected vector search to return at least one result");
        assertEquals("doc3", results.getFirst().document().id(),
                "Expected document containing the rare term 'lucene' to rank first");
    }

    @Test
    void searchShouldRankProgrammingDocumentFirstForProgrammingQuery() {
        final Tokenizer tokenizer = Tokenizers.whitespace();
        final Normalizer normalizer = Normalizers.english();
        final Corpus corpus = Corpora.inMemory(Corpora.CorpusStatisticsRefreshMode.EAGER);
        final InvertedIndex invertedIndex = InvertedIndexes.inMemory();
        final Indexer indexer = Indexers.lexical(corpus, invertedIndex, tokenizer, normalizer);
        final DocumentWeighter documentWeighter = Weighters.tfIdf(tokenizer, invertedIndex);
        final Vectorizer<SparseDocumentVector> vectorizer = Vectorizers.sparse();
        final DocumentVectorStore<SparseDocumentVector> documentVectorStore = DocumentVectorStores.inMemory();

        final Document doc1 = document("doc1", "java search engine");
        final Document doc2 = document("doc2", "java programming language");
        final Document doc3 = document("doc3", "lucene inverted index search");

        indexer.index(doc1);
        indexer.index(doc2);
        indexer.index(doc3);

        storeVector(corpus, documentWeighter, vectorizer, documentVectorStore, doc1);
        storeVector(corpus, documentWeighter, vectorizer, documentVectorStore, doc2);
        storeVector(corpus, documentWeighter, vectorizer, documentVectorStore, doc3);

        final Searcher searcher = Searchers.vector(
                tokenizer,
                normalizer,
                documentWeighter,
                vectorizer,
                Similarities.cosine(),
                corpus,
                documentVectorStore
        );

        final List<SearchResult> results = searcher.search("programming");

        assertFalse(results.isEmpty(), "Expected vector search to return at least one result");
        assertEquals("doc2", results.getFirst().document().id(),
                "Expected programming-focused document to rank first for query 'programming'");
    }

    private static void storeVector(final Corpus corpus,
                                    final DocumentWeighter documentWeighter,
                                    final Vectorizer<SparseDocumentVector> vectorizer,
                                    final DocumentVectorStore<SparseDocumentVector> documentVectorStore,
                                    final Document document) {
        final SparseDocumentVector vector = vectorizer.vectorize(documentWeighter.weigh(corpus, document));
        documentVectorStore.store(document.id(), vector);
    }

    private static Document document(final String id, final String text) {
        return Document.builder()
                .id(id)
                .rawContent(text)
                .normalizedContent(text)
                .build();
    }
}
