/**
 * Search abstraction over indexed documents.
 * <p>
 * {@link codex.ir.search.Searcher} executes queries against the inverted index
 * or vector store. {@link codex.ir.search.Searchers} provides factory methods:
 * </p>
 * <ul>
 *   <li><b>Lexical</b> — tokenizes the query, looks up the inverted index,
 *       scores with a {@link codex.ir.ranking.Ranker}</li>
 *   <li><b>Vector</b> — vectorizes the query, computes cosine similarity
 *       against all stored document vectors</li>
 * </ul>
 * Results are returned as {@link codex.ir.search.SearchResult} records with
 * document, score, and matched terms.
 */
package codex.ir.search;
