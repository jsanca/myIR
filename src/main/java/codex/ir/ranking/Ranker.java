package codex.ir.ranking;

import codex.ir.indexer.Posting;

import java.util.Collection;

/**
 * Abstraction for ranking strategies used during information retrieval.
 *
 * A {@code Ranker} encapsulates the scoring logic used to evaluate how
 * relevant a document is for a given query term. Different implementations
 * may apply different statistical models (e.g., TF-IDF, BM25).
 *
 * The ranker typically relies on corpus statistics (such as the total
 * number of documents) and index statistics (such as document frequency)
 * to compute scores.
 *
 * This interface exposes both term-level statistics and per-document
 * scoring operations so that ranking logic remains fully encapsulated
 * behind the ranking strategy.
 * @jsanca & elo
 */
public interface Ranker {

    /**
     * Computes the inverse document frequency (IDF) for a term.
     *
     * IDF measures how informative a term is across the corpus.
     * Terms that appear in many documents receive lower scores,
     * while rare terms receive higher scores.
     *
     * Typical formula:
     *
     * <pre>
     * idf = log(N / df)
     * </pre>
     *
     * where:
     * <ul>
     *   <li>N  = total number of documents in the corpus</li>
     *   <li>df = number of documents containing the term</li>
     * </ul>
     *
     * Implementations may apply smoothing or alternative formulas
     * depending on the ranking model.
     *
     * @param term normalized term whose IDF should be computed
     * @return inverse document frequency value for the term
     */
    double idf(final String term);

    /**
     * Computes the score contribution of a term for a specific posting.
     *
     * This method encapsulates how a ranking model transforms the
     * combination of query term statistics and document-level term
     * statistics into a score contribution.
     *
     * For example, a TF-IDF based implementation may compute:
     *
     * <pre>
     * score = tf(term, document) * idf(term)
     * </pre>
     *
     * while other models may use alternative formulas.
     *
     * @param term normalized term contributing to the score
     * @param posting posting describing how the term appears in a document
     * @return score contribution for that term-document pair
     */
    double score(final String term, final Posting posting);

}
