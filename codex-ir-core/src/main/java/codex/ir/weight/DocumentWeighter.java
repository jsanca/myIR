package codex.ir.weight;

import codex.ir.Document;
import codex.ir.corpus.CorpusSnapshot;

import java.util.Map;

/**
 * Computes per-term weights for a document before vectorization.
 *
 * <p>The returned map is expressed in the normalized term space, where each
 * key is a normalized term and each value is the weight assigned by the
 * concrete strategy (for example binary, TF, or TF-IDF).</p>
 *
 * <p>This abstraction keeps weighting reusable outside the indexing pipeline,
 * so the same logic can later be used for isolated document vectorization,
 * centroid construction, summaries, or other vector-oriented workflows.</p>
 *
 * <p>Implementations consume a {@link CorpusSnapshot} rather than the live
 * {@link codex.ir.corpus.Corpus}. Callers must take a snapshot after ingestion
 * is complete before calling this method.</p>
 * @author jsanca &amp; elo
 */
public interface DocumentWeighter {

    /**
     * Computes normalized term weights for the supplied document in the context
     * of the provided corpus snapshot.
     *
     * @param snapshot immutable corpus view providing global statistics needed by the weighting strategy
     * @param document document to weigh
     * @return map of normalized term to computed weight
     */
    Map<String, Double> weigh(CorpusSnapshot snapshot, Document document);
}
