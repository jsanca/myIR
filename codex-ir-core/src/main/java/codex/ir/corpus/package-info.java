/**
 * Document corpus management.
 * <p>
 * {@link codex.ir.corpus.Corpus} stores, retrieves, and iterates documents
 * by identifier, providing aggregate statistics ({@link codex.ir.corpus.CorpusStatistics})
 * for ranking. Factories in {@link codex.ir.corpus.Corpora} offer in-memory
 * variants with eager or debounced statistics refresh.
 * </p>
 */
package codex.ir.corpus;
