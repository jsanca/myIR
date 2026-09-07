package codex.ir.ranking;

import codex.ir.Document;
import codex.ir.corpus.CorpusSnapshot;
import codex.ir.corpus.CorpusStatistics;
import codex.ir.indexer.IndexSnapshot;
import codex.ir.indexer.Posting;
import codex.ir.util.TermWeightingUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory and container for {@link Ranker} implementations.
 *
 * <p>This utility class exposes static factory methods used to create
 * ranking strategies for the retrieval pipeline. It follows the
 * same pattern used elsewhere in the project where concrete
 * implementations are hidden behind simple factory methods.
 *
 * <p>Example usage:
 * <pre>
 * Ranker ranker = Rankers.tfIdf(corpus, invertedIndex);
 * </pre>
 *
 * <p>Each implementation's {@link Ranker#evaluate} method is the single scoring source
 * of truth. {@link Ranker#score} delegates to it by interface default.
 *
 * @author jsanca &amp; elo
 */
public final class Rankers {

    private Rankers() {}

    /**
     * Creates a ranker based on the binary ranking model.
     *
     * <p>The returned {@link Ranker} implementation only checks whether
     * a term is present in a document. If present, the score
     * contribution is {@code 1.0}; otherwise it is {@code 0.0}.
     *
     * @return a ranker implementing binary term presence scoring
     */
    public static Ranker binary() {
        return new BinaryRanker();
    }

    /**
     * Creates a ranker based on the TF-IDF ranking model.
     *
     * <p>The returned {@link Ranker} implementation computes inverse document frequency
     * (IDF) and uses it together with term frequency (TF) from document metadata to score
     * documents. Callers must take snapshots after ingestion is complete before constructing
     * the ranker — the ranker sees only the state captured at snapshot time.
     *
     * @param corpus frozen corpus view providing document-level statistics
     * @param index  frozen index view used to obtain document frequency
     * @return a ranker capable of computing TF-IDF values for terms
     */
    public static Ranker tfIdf(final CorpusSnapshot corpus, final IndexSnapshot index) {
        return new TfIdfRanker(corpus, index);
    }

    /**
     * Creates a ranker based on the BM25 ranking model.
     *
     * <p>The returned {@link Ranker} implementation computes BM25 using term frequency and
     * document length from document metadata, and document frequency from the index snapshot.
     * Callers must take snapshots after ingestion is complete before constructing the ranker.
     *
     * @param corpus frozen corpus view providing document-level statistics
     * @param index  frozen index view used to obtain document frequency
     * @return a ranker capable of computing BM25 values for terms
     */
    public static Ranker bm25(final CorpusSnapshot corpus, final IndexSnapshot index) {
        return new Bm25Ranker(corpus, index);
    }

    // -----------------------------------------------------------------------
    // BinaryRanker
    // -----------------------------------------------------------------------

    /**
     * Binary {@link Ranker}: every matched term contributes {@code 1.0}; everything else is {@code 0.0}.
     *
     * <p>IDF is always {@code 1.0} (neutral) so the contract remains consistent across strategies.
     */
    private static class BinaryRanker implements Ranker {

        @Override
        public double idf(final String term) {
            return 1.0;
        }

        @Override
        public TermScoring evaluate(final String term, final Posting posting, final RankingContext context) {
            if (term == null || term.isBlank() || posting == null) {
                final String safeTerm = term != null ? term : "";
                return new BinaryTermScoring(safeTerm, 0.0, Optional.empty(), 0.0);
            }
            final Optional<FieldBoost> fieldBoost = TermScorings.computeFieldBoost(posting, context);
            final double contribution = TermScorings.applyBoost(1.0, fieldBoost);
            return new BinaryTermScoring(term, 1.0, fieldBoost, contribution);
        }
    }

    // -----------------------------------------------------------------------
    // TfIdfRanker
    // -----------------------------------------------------------------------

    /**
     * TF-IDF {@link Ranker} using sublinear TF and classic IDF ({@code log(N/df)}).
     */
    private static class TfIdfRanker implements Ranker {

        private final CorpusSnapshot corpus;
        private final IndexSnapshot index;
        private final Map<String, Double> idfCache = new ConcurrentHashMap<>();

        TfIdfRanker(final CorpusSnapshot corpus, final IndexSnapshot index) {
            this.corpus = corpus;
            this.index = index;
        }

        @Override
        public double idf(final String term) {
            if (term == null || term.isBlank()) {
                return 0;
            }
            return idfCache.computeIfAbsent(term, t -> {
                final int corpusSize = corpus.statistics().documentCount();
                final List<Posting> postings = index.getPostings(t);
                final int documentFrequency = (postings == null) ? 0 : postings.size();
                if (documentFrequency == 0 || corpusSize == 0) {
                    return 0.0;
                }
                return TermWeightingUtils.classicIdf(corpusSize, documentFrequency);
            });
        }

        @Override
        public TermScoring evaluate(final String term, final Posting posting, final RankingContext context) {
            if (term == null || term.isBlank() || posting == null) {
                final String safeTerm = term != null ? term : "";
                return new TfIdfTermScoring(safeTerm, 0, 0.0, 0.0, 0.0, Optional.empty(), 0.0);
            }
            final int tf = posting.termFrequency();
            if (tf <= 0) {
                return new TfIdfTermScoring(term, 0, 0.0, idf(term), 0.0, Optional.empty(), 0.0);
            }
            final double sublinearTf = TermWeightingUtils.sublinearTf(tf);
            final double idfValue = idf(term);
            final double base = sublinearTf * idfValue;
            final Optional<FieldBoost> fieldBoost = TermScorings.computeFieldBoost(posting, context);
            final double contribution = TermScorings.applyBoost(base, fieldBoost);
            return new TfIdfTermScoring(term, tf, sublinearTf, idfValue, base, fieldBoost, contribution);
        }
    }

    // -----------------------------------------------------------------------
    // Bm25Ranker
    // -----------------------------------------------------------------------

    /**
     * BM25 {@link Ranker} with hardcoded k₁=1.2 and b=0.75.
     *
     * <p>Formula:
     * <pre>
     * normalization = 1 - b + b × (dl / avgdl)
     * score = idf(t) × (tf × (k1 + 1)) / (tf + k1 × normalization)
     * </pre>
     *
     * <p>The actual k₁ and b values are exposed on {@link Bm25TermScoring} returned by
     * {@link #evaluate} so callers can inspect which parameters produced a given score.
     * Making them publicly configurable is deferred to a future task.
     */
    private static class Bm25Ranker implements Ranker {

        private static final double DEFAULT_K1 = 1.2;
        private static final double DEFAULT_B = 0.75;

        private final CorpusSnapshot corpus;
        private final IndexSnapshot index;
        private final double k1;
        private final double b;
        private final Map<String, Double> idfCache = new ConcurrentHashMap<>();

        Bm25Ranker(final CorpusSnapshot corpus, final IndexSnapshot index) {
            this(corpus, index, DEFAULT_K1, DEFAULT_B);
        }

        Bm25Ranker(final CorpusSnapshot corpus, final IndexSnapshot index, final double k1, final double b) {
            this.corpus = corpus;
            this.index = index;
            this.k1 = k1;
            this.b = b;
        }

        @Override
        public double idf(final String term) {
            if (term == null || term.isBlank()) {
                return 0;
            }
            return idfCache.computeIfAbsent(term, t -> {
                final int corpusSize = corpus.statistics().documentCount();
                final List<Posting> postings = index.getPostings(t);
                final int documentFrequency = (postings == null) ? 0 : postings.size();
                if (documentFrequency == 0 || corpusSize == 0) {
                    return 0.0;
                }
                return TermWeightingUtils.bm25Idf(corpusSize, documentFrequency);
            });
        }

        @Override
        public TermScoring evaluate(final String term, final Posting posting, final RankingContext context) {
            if (term == null || term.isBlank() || posting == null) {
                final String safeTerm = term != null ? term : "";
                return new Bm25TermScoring(safeTerm, 0, 0.0, 0, 0.0, k1, b, 0.0, 0.0, Optional.empty(), 0.0);
            }
            final int tf = posting.termFrequency();
            final int documentLength = extractDocumentLength(corpus, posting);
            final double averageDocumentLength = corpus.statistics().averageDocumentLength();
            final double idfValue = idf(term);
            if (tf <= 0 || documentLength <= 0 || averageDocumentLength <= 0) {
                return new Bm25TermScoring(term, tf, idfValue, documentLength, averageDocumentLength,
                        k1, b, 0.0, 0.0, Optional.empty(), 0.0);
            }
            final double normalization = 1.0 - b + b * (documentLength / averageDocumentLength);
            final double numerator = tf * (k1 + 1.0);
            final double denominator = tf + k1 * normalization;
            final double base = denominator == 0.0 ? 0.0 : idfValue * (numerator / denominator);
            final Optional<FieldBoost> fieldBoost = TermScorings.computeFieldBoost(posting, context);
            final double contribution = TermScorings.applyBoost(base, fieldBoost);
            return new Bm25TermScoring(term, tf, idfValue, documentLength, averageDocumentLength,
                    k1, b, normalization, base, fieldBoost, contribution);
        }
    }

    // -----------------------------------------------------------------------
    // Shared helpers
    // -----------------------------------------------------------------------

    private static int extractDocumentLength(final CorpusSnapshot corpus, final Posting posting) {
        final Optional<Document> documentOpt = corpus.get(posting.documentId());
        if (documentOpt.isEmpty() || documentOpt.get().metadata() == null) {
            return 0;
        }
        final Integer documentLength = documentOpt.get().metadata().length();
        if (documentLength == null) {
            return 0;
        }
        return documentLength;
    }
}
