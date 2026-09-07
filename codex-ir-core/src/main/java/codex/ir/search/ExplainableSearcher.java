package codex.ir.search;

import codex.ir.ranking.ScoreExplanation;

import java.util.Optional;

/**
 * Capability interface for {@link Searcher} implementations that can explain a score.
 *
 * <p>Not every {@link Searcher} supports explanation — in particular, vector-based
 * searchers cannot attribute scores to discrete term postings. Callers that need
 * explanation must check {@code instanceof ExplainableSearcher} at the call site (T-25/T-26).
 *
 * <p>Implementations must ensure that {@link #explain} uses the same query-analysis
 * pipeline (tokenization + normalization) as {@link Searcher#searchDetailed}, so the
 * analyzed terms driving both paths are identical (UC-6, T-27).
 *
 * @author jsanca &amp; elo
 */
public interface ExplainableSearcher extends Searcher {

    /**
     * Returns a score explanation for the given document relative to the query.
     *
     * <p>The explanation is built from the same analyzed-query terms used in
     * {@link Searcher#searchDetailed}. Only terms that produce a posting for
     * {@code documentId} appear in {@link ScoreExplanation#contributions()}.
     *
     * @param query      raw user query; treated identically to {@link Searcher#searchDetailed}
     * @param documentId identifier of the document to explain
     * @return {@code Optional.of(explanation)} when at least one analyzed term matches the
     *         document; {@code Optional.empty()} when the query is null/blank, the document is
     *         unknown, the query analyzes to empty, or no analyzed term matches
     */
    Optional<ScoreExplanation> explain(String query, String documentId);
}
