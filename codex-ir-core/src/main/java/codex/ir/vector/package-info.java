/**
 * Sparse vector representation and similarity computation.
 * <p>
 * {@link codex.ir.vector.SparseDocumentVector} maps vocabulary dimension IDs
 * to weights with a precomputed Euclidean norm. {@link codex.ir.vector.Similarity}
 * computes similarity between vectors (cosine via {@link codex.ir.vector.Similarities}).
 * {@link codex.ir.vector.SimilarityResult} and {@link codex.ir.vector.SimilarityMatch}
 * provide per-dimension contribution explanations.
 * {@link codex.ir.vector.Vectorizer} converts weighted terms into sparse vectors
 * using the shared {@link codex.ir.corpus.vector.Vocabulary}.
 * </p>
 */
package codex.ir.vector;
