package codex.ir.ranking;

import codex.ir.corpus.Corpus;
import codex.ir.indexer.InvertedIndex;
import codex.ir.indexer.Posting;

import java.util.Collection;
import java.util.List;
import java.util.Map;
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
     * Creates a ranker based on the TF-IDF ranking model.
     *
     * The returned {@link Ranker} implementation computes inverse
     * document frequency (IDF) and uses it together with term
     * frequency (TF) from postings to score documents.
     *
     * @param corpus corpus providing document-level statistics
     * @param index inverted index used to obtain document frequency
     * @return a ranker capable of computing TF-IDF values for terms
     */
    public static Ranker tfIdf(final Corpus corpus, final InvertedIndex index) {
        return new TfIdfRanker(corpus, index);
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
                final int corpusSize = corpus.size();

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
         * @param posting posting describing the term occurrences in a document
         * @return TF-IDF score contribution for the term-document pair
         */
        @Override
        public double score(final String term, final Posting posting) {
            if (term == null || term.isBlank() || posting == null) {
                return 0;
            }

            final int tf = posting.termFrequency();
            if (tf <= 0) {
                return 0;
            }

            // sublinear TF scaling: 1 + log(tf)
            final double sublinearTf = 1.0 + Math.log(tf);

            return sublinearTf * idf(term);
        }


    }
}
