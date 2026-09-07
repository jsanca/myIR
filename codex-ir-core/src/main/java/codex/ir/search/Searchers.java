package codex.ir.search;

import codex.ir.corpus.CorpusSnapshot;
import codex.ir.corpus.vector.Vocabulary;
import codex.ir.indexer.IndexSnapshot;
import codex.ir.normalizer.Normalizer;
import codex.ir.ranking.RankingContext;
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

    /**
     * Creates a field-aware lexical searcher that applies {@code rankingContext} when
     * scoring each matching posting.
     *
     * <p>Passing {@link RankingContext#neutral()} produces scores identical to the
     * non-context overload. Pass a context with explicit
     * {@link codex.ir.ranking.FieldWeights} to boost terms that appear in high-value
     * fields (e.g. give "title" a weight of 2.0 to rank title matches higher).</p>
     */
    public static Searcher lexical(final IndexSnapshot invertedIndex,
                                   final CorpusSnapshot corpus,
                                   final Tokenizer tokenizer,
                                   final Normalizer normalizer,
                                   final Ranker ranker,
                                   final RankingContext rankingContext) {

        return new SimpleSearcher(invertedIndex, corpus, tokenizer, normalizer, ranker, rankingContext);
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
