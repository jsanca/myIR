package codex.ir.ranking;

import codex.ir.corpus.Corpus;
import codex.ir.indexer.InvertedIndex;
import codex.ir.indexer.Posting;

import java.util.Collection;
import java.util.List;

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

            // idf = log(N / df)
            final int corpusSize = corpus.size();
            final int documentFrequency = index.getPostings(term).size(); // how many docs match the term

            if (documentFrequency == 0) {
                return 0;
            }

            return Math.log((double) corpusSize / documentFrequency);
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

            return posting.termFrequency() * idf(term);
        }


    }
}
