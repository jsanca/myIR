package codex.ir.search;

import codex.ir.corpus.Corpus;
import codex.ir.corpus.vector.Vocabulary;
import codex.ir.normalizer.Normalizer;
import codex.ir.tokenizer.Tokenizer;
import codex.ir.vector.Similarity;
import codex.ir.vector.SparseDocumentVector;
import codex.ir.vector.Vectorizer;
import codex.ir.vector.store.DocumentVectorStore;
import codex.ir.weight.DocumentWeighter;

import java.util.Objects;

public final class Searchers {

    private Searchers() {}

    public static Searcher vector(final Tokenizer tokenizer,
                                  final Normalizer normalizer,
                                  final DocumentWeighter documentWeighter,
                                  final Vectorizer<SparseDocumentVector> vectorizer,
                                  final Similarity<SparseDocumentVector> similarity,
                                  final Corpus corpus,
                                  final DocumentVectorStore documentVectorStore,
                                  final Vocabulary vocabulary,
                                  final double threshold) {

        return new VectorSearcher(corpus, vocabulary, vectorizer, documentWeighter,
                documentVectorStore, tokenizer, normalizer, similarity, threshold);
    }
}
