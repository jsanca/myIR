package codex.ir.ranking;

import codex.ir.indexer.Posting;

/**
 * Abstraction for ranking strategies used during information retrieval.
 *
 * <p>A {@code Ranker} encapsulates the scoring logic used to evaluate how
 * relevant a document is for a given query term. Different implementations
 * may apply different statistical models (e.g., TF-IDF, BM25).
 *
 * <p>The ranker typically relies on corpus statistics (such as the total
 * number of documents) and index statistics (such as document frequency)
 * to compute scores.
 *
 * <h2>Single source of truth (IR-5)</h2>
 * <p>{@link #evaluate(String, Posting, RankingContext)} is the fundamental method.
 * It computes the scoring formula once and returns a typed {@link TermScoring} that
 * carries every intermediate needed for a score explanation. {@link #score} is a
 * default projection over {@code evaluate}:
 *
 * <pre>
 * score(term, posting, context)
 *     → evaluate(term, posting, context).contribution()
 * </pre>
 *
 * <p>Ranker implementers must not override {@code score(term, posting, context)};
 * doing so would reintroduce a second scoring code path that could drift from
 * the explanation path.
 *
 * <h2>Hot-path allocation (accepted trade-off)</h2>
 * <p>Every call to {@code score(term, posting)} now allocates one {@link TermScoring}
 * record per matched {@code (term, posting)} pair. This is an intentional cost of
 * keeping a single scoring source of truth. For the current in-memory corpus sizes
 * (thousands of documents) the allocation is negligible.
 *
 * @author jsanca &amp; elo
 */
public interface Ranker {

    /**
     * Computes the inverse document frequency (IDF) for a term.
     *
     * <p>IDF measures how informative a term is across the corpus.
     * Terms that appear in many documents receive lower scores,
     * while rare terms receive higher scores.
     *
     * <p>Typical formula:
     * <pre>
     * idf = log(N / df)
     * </pre>
     *
     * where N is the total number of documents and df is the document frequency.
     *
     * @param term normalized term whose IDF should be computed
     * @return inverse document frequency value for the term
     */
    double idf(String term);

    /**
     * Evaluates the score contribution of a term for a specific posting and context.
     *
     * <p>This is the fundamental method every ranker must implement. It computes the
     * ranker's formula once and returns a typed {@link TermScoring} that carries every
     * meaningful intermediate together with the final {@link TermScoring#contribution()}.
     *
     * <p>The {@link TermScoring#contribution()} value is identical to what
     * {@link #score(String, Posting, RankingContext)} returns, by construction:
     * {@code score} is a default method that delegates to this method.
     *
     * <p>When {@code term} is {@code null}/blank or {@code posting} is {@code null},
     * implementations must return a well-formed {@code TermScoring} with
     * {@code contribution == 0.0} rather than throwing.
     *
     * @param term    normalized query term
     * @param posting posting identifying the matching document
     * @param context per-request ranking configuration carrying field weights
     * @return typed scoring result; never {@code null}
     */
    TermScoring evaluate(String term, Posting posting, RankingContext context);

    /**
     * Computes the field-boosted score for a term-document pair.
     *
     * <p>This default delegates to {@link #evaluate} and returns
     * {@link TermScoring#contribution()}. Ranker implementers must not override this
     * method — the interface default is the only scoring path.
     *
     * @param term    normalized query term
     * @param posting posting for the term in a candidate document
     * @param context per-request ranking configuration carrying field weights
     * @return field-boosted score contribution
     */
    default double score(String term, Posting posting, RankingContext context) {
        return evaluate(term, posting, context).contribution();
    }

    /**
     * Computes the score for a term-document pair using a neutral ranking context.
     *
     * <p>Equivalent to {@code score(term, posting, RankingContext.neutral())}.
     *
     * @param term    normalized term contributing to the score
     * @param posting posting describing how the term appears in a document
     * @return score contribution for that term-document pair
     */
    default double score(String term, Posting posting) {
        return score(term, posting, RankingContext.neutral());
    }
}
