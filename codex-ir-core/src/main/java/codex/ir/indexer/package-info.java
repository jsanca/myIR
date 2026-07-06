/**
 * Indexing pipeline and inverted index.
 * <p>
 * {@link codex.ir.indexer.Indexer} coordinates document preprocessing, lexical
 * indexing ({@link codex.ir.indexer.InvertedIndex}), and vector indexing.
 * {@link codex.ir.indexer.Indexers} provides factory methods for assembling
 * lexical-only, vector-only, or combined indexing pipelines.
 * </p>
 * <p>
 * {@link codex.ir.indexer.Posting} represents term occurrences within a document.
 * Each posting carries the whole-document {@code termFrequency} and {@code positions},
 * plus a {@code fieldFrequencies} map (IR-3) that records per-field occurrence counts
 * for structured-field documents. Raw-content documents have an empty {@code fieldFrequencies}.
 * </p>
 * <p>
 * {@link codex.ir.indexer.PreprocessedDocument} is the analysis artifact produced
 * by the preprocessing stage, carrying the enriched {@link codex.ir.Document} and the
 * ordered normalized token list.
 * {@link codex.ir.indexer.FieldAnalyzedDocument} wraps a {@link codex.ir.indexer.PreprocessedDocument}
 * and adds per-field token sequences ({@link codex.ir.indexer.FieldTokenSequence}), one per
 * non-blank field. Field sequences drive the IR-3 per-field frequency population in postings.
 * </p>
 * <p>
 * {@link codex.ir.indexer.IndexSnapshot} is an immutable, point-in-time view of the index
 * produced after ingestion. All read-path components consume snapshots rather than the live index.
 * </p>
 */
package codex.ir.indexer;
