package codex.ir.ranking;

import codex.ir.Document;
import codex.ir.corpus.Corpus;
import codex.ir.corpus.CorpusStatistics;
import codex.ir.indexer.Indexer;
import codex.ir.indexer.InvertedIndex;
import codex.ir.indexer.Posting;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory and container for {@link Ranker} implementations.
 *
 * This utility class exposes static factory methods used to create
 * ranking strategies for the retrieval pipeline. It follows the
 * same pattern used elsewhere in the project where concrete
 * implementations are hidden behind simple factory methods.
 *
 * Example usage:
 *
 * <pre>
 * Ranker ranker = Rankers.tfIdf(corpus, invertedIndex);
 * </pre>
 *
 * Additional ranking strategies (e.g. BM25) can be added here
 * without exposing their internal implementation classes.
 * @author jsanca & elo
 */
public final class Rankers {

    private Rankers() {}

    /**
     * Creates a ranker based on the binary ranking model.
     *
     * The returned {@link Ranker} implementation only checks whether
     * a term is present in a document. If present, the score
     * contribution is {@code 1.0}; otherwise it is {@code 0.0}.
     *
     * This ranker is useful as a simple baseline for validating
     * retrieval behavior before introducing frequency-based or
     * probabilistic models.
     *
     * @return a ranker implementing binary term presence scoring
     */
    public static Ranker binary() {
        return new BinaryRanker();
    }
    /**
     * Creates a ranker based on the TF-IDF ranking model.
     *
     * The returned {@link Ranker} implementation computes inverse
     * document frequency (IDF) and uses it together with term
     * frequency (TF) from document metadata to score documents.
     *
     * @param corpus corpus providing document-level statistics
     * @param index inverted index used to obtain document frequency
     * @return a ranker capable of computing TF-IDF values for terms
     */
    public static Ranker tfIdf(final Corpus corpus, final InvertedIndex index) {
        return new TfIdfRanker(corpus, index);
    }

    /**
     * Creates a ranker based on the BM25 ranking model.
     *
     * The returned {@link Ranker} implementation computes BM25 using
     * term frequency and document length from document metadata, and
     * document frequency from the inverted index.
     *
     * @param corpus corpus providing document-level statistics
     * @param index inverted index used to obtain document frequency
     * @return a ranker capable of computing BM25 values for terms
     */
    public static Ranker bm25(final Corpus corpus, final InvertedIndex index) {
        return new Bm25Ranker(corpus, index);
    }

    /**
     * Simple {@link Ranker} implementation based on binary term presence.
     *
     * This implementation does not use real inverse document frequency.
     * Instead, it returns a neutral value of {@code 1.0} for IDF so the
     * contract remains consistent across ranking strategies.
     *
     * A term contributes:
     * <ul>
     *   <li>{@code 1.0} if it is present in the document</li>
     *   <li>{@code 0.0} if the term is invalid or the posting is missing</li>
     * </ul>
     */
    private static class BinaryRanker implements Ranker {

        @Override
        public double idf(final String term) {
            return 1.0;
        }

        @Override
        public double score(final String term, final Posting posting) {
            if (term == null || term.isBlank() || posting == null) {
                return 0;
            }

            return 1.0;
        }
    }

    /**
     * Default {@link Ranker} implementation computing inverse
     * document frequency (IDF).
     *
     * This implementation relies on two statistics:
     * <ul>
     *   <li>N  – total number of documents in the corpus</li>
     *   <li>df – number of documents containing the term</li>
     * </ul>
     *
     * Using the classical formula:
     *
     * <pre>
     * idf = log(N / df)
     * </pre>
     *
     * This class is intentionally package-private and exposed
     * through the {@link Rankers} factory methods.
     */
    private static class TfIdfRanker implements Ranker {

        private final Corpus corpus;
        private final InvertedIndex index;
        private final Map<String, Double> idfCache = new ConcurrentHashMap<>();

        public TfIdfRanker(final Corpus corpus, final InvertedIndex index) {
            this.corpus = corpus;
            this.index = index;
        }

        /**
         * Computes the inverse document frequency for a term.
         *
         * @param term normalized term
         * @return IDF value based on corpus and index statistics
         */
        @Override
        public double idf(final String term) {
            if (term == null || term.isBlank()) {
                return 0;
            }

            // compute or retrieve from cache
            return idfCache.computeIfAbsent(term, t -> {
                final int corpusSize = corpus.statistics().documentCount();

                final List<Posting> postings = index.getPostings(t);
                final int documentFrequency = (postings == null) ? 0 : postings.size();

                if (documentFrequency == 0 || corpusSize == 0) {
                    return 0.0;
                }

                // classical idf = log(N / df)
                return Math.log((double) corpusSize / documentFrequency);
            });
        }

        /**
         * Computes the TF-IDF contribution of a term for a specific posting.
         *
         * @param term normalized term
         * @param posting posting identifying the matching document for the term
         * @return TF-IDF score contribution for the term-document pair
         */
        @Override
        public double score(final String term, final Posting posting) {
            if (term == null || term.isBlank() || posting == null) {
                return 0;
            }

            final int tf = extractTermFrequency(corpus, posting, term);
            if (tf <= 0) {
                return 0;
            }

            // sublinear TF scaling: 1 + log(tf)
            final double sublinearTf = 1.0 + Math.log(tf);

            return sublinearTf * idf(term);
        }


    }
    private static int extractTermFrequency(final Corpus corpus, final Posting posting, final String term) {
        final Optional<Document> documentOpt = corpus.get(posting.documentId());
        if (documentOpt.isEmpty() || documentOpt.get().metadata() == null) {
            return 0;
        }

        final Document document = documentOpt.get();
        final Map<String, Integer> termFrequencies = document.metadata().termFrequencies();
        if (termFrequencies == null || termFrequencies.isEmpty()) {
            return 0;
        }

        return termFrequencies.getOrDefault(term, 0);
    }

    private static int extractDocumentLength(final Corpus corpus, final Posting posting) {
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

    /**
     * Default {@link Ranker} implementation computing BM25 scores.
     *
     * This implementation relies on:
     * <ul>
     *   <li>N     – total number of documents in the corpus</li>
     *   <li>df    – number of documents containing the term</li>
     *   <li>tf    – term frequency in the current document</li>
     *   <li>dl    – length of the current document</li>
     *   <li>avgdl – average document length in the corpus</li>
     * </ul>
     *
     * Using the BM25 formula:
     *
     * <pre>
     * score = idf(t) * (tf * (k1 + 1)) / (tf + k1 * (1 - b + b * (dl / avgdl)))
     * </pre>
     */
    private static class Bm25Ranker implements Ranker {

        private static final double DEFAULT_K1 = 1.2;
        private static final double DEFAULT_B = 0.75;

        private final Corpus corpus;
        private final InvertedIndex index;
        // Saturation
        private final double k1;
        // Length normalization
        private final double b;
        private final Map<String, Double> idfCache = new ConcurrentHashMap<>();

        public Bm25Ranker(final Corpus corpus, final InvertedIndex index) {
            this(corpus, index, DEFAULT_K1, DEFAULT_B);
        }

        public Bm25Ranker(
                final Corpus corpus,
                final InvertedIndex index,
                final double k1,
                final double b
        ) {
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

                return Math.log(1.0 + ((double) (corpusSize - documentFrequency + 0.5)
                        / (documentFrequency + 0.5)));
            });
        }

        @Override
        public double score(final String term, final Posting posting) {
            if (term == null || term.isBlank() || posting == null) {
                return 0;
            }

            final int tf = extractTermFrequency(corpus, posting, term);
            if (tf <= 0) {
                return 0;
            }

            final Integer documentLengthValue = extractDocumentLength(corpus, posting);
            if (Objects.isNull(documentLengthValue) || documentLengthValue <= 0) {
                return 0;
            }

            final CorpusStatistics statistics = corpus.statistics();
            final double averageDocumentLength = statistics.averageDocumentLength();
            if (averageDocumentLength <= 0) {
                return 0;
            }

            final double documentLength = documentLengthValue.doubleValue();
            final double normalization = 1.0 - b + b * (documentLength / averageDocumentLength);
            final double numerator = tf * (k1 + 1.0);
            final double denominator = tf + k1 * normalization;

            if (denominator == 0) {
                return 0;
            }

            return idf(term) * (numerator / denominator);
        }
    }
}
