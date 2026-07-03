/**
 * Term weighting strategies for vector-space retrieval.
 * <p>
 * {@link codex.ir.weight.DocumentWeighter} computes per-term weights for a
 * document in corpus context. {@link codex.ir.weight.Weighters} provides:
 * </p>
 * <ul>
 *   <li><b>Term frequency</b> — raw TF weights</li>
 *   <li><b>TF-IDF</b> — TF × IDF weighting for sparse vector construction</li>
 * </ul>
 */
package codex.ir.weight;
