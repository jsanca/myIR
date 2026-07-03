/**
 * Indexing pipeline and inverted index.
 * <p>
 * {@link codex.ir.indexer.Indexer} coordinates document preprocessing, lexical
 * indexing ({@link codex.ir.indexer.InvertedIndex}), and vector indexing.
 * {@link codex.ir.indexer.Indexers} provides factory methods for assembling
 * lexical-only, vector-only, or combined indexing pipelines.
 * {@link codex.ir.indexer.Posting} represents term occurrences within a document.
 * </p>
 */
package codex.ir.indexer;
