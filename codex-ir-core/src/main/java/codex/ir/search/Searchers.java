package codex.ir.search;

import codex.ir.corpus.CorpusSnapshot;
import codex.ir.corpus.vector.Vocabulary;
import codex.ir.indexer.IndexSnapshot;
import codex.ir.normalizer.Normalizer;
import codex.ir.ranking.Ranker;
import codex.ir.tokenizer.Tokenizer;
import codex.ir.vector.Similarity;
import codex.ir.vector.SparseDocumentVector;
import codex.ir.vector.Vectorizer;
import codex.ir.vector.store.DocumentVectorStore;
import codex.ir.weight.DocumentWeighter;

import java.util.Objects;

public final class Searchers {

    private Searchers() {}

    public static Searcher lexical(final IndexSnapshot invertedIndex,
                                   final CorpusSnapshot corpus,
                                   final Tokenizer tokenizer,
                                   final Normalizer normalizer,
                                   final Ranker ranker) {

        return new SimpleSearcher(invertedIndex, corpus, tokenizer, normalizer, ranker);
    }

    public static Searcher vector(final Tokenizer tokenizer,
                                  final Normalizer normalizer,
                                  final DocumentWeighter documentWeighter,
                                  final Vectorizer<SparseDocumentVector> vectorizer,
                                  final Similarity<SparseDocumentVector> similarity,
                                  final CorpusSnapshot corpus,
                                  final DocumentVectorStore documentVectorStore,
                                  final Vocabulary vocabulary,
                                  final double threshold) {

        return new VectorSearcher(corpus, vocabulary, vectorizer, documentWeighter,
                documentVectorStore, tokenizer, normalizer, similarity, threshold);
    }
}
