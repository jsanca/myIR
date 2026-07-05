package codex.ir.corpus;

import codex.ir.Document;

import java.util.Optional;

/**
 * Immutable, point-in-time view of a corpus captured after an ingestion round.
 *
 * <p>A {@code CorpusSnapshot} is produced by {@link Corpus#snapshot()} once all writes
 * for a given batch are complete. The snapshot captures the document collection and
 * derived statistics at that instant. Subsequent writes to the originating
 * {@link Corpus} do not affect it.</p>
 *
 * <p>All read-path components —
 * {@link codex.ir.ranking.Ranker}, {@link codex.ir.search.Searcher}, and
 * {@link codex.ir.weight.DocumentWeighter} — consume a {@code CorpusSnapshot} rather
 * than the live {@link Corpus}. This enforces a clear write/read boundary: ingestion
 * writes to the mutable corpus; search and ranking read from a stable, frozen view.</p>
 *
 * <p>Implementations must be safe to share across threads without external synchronization.</p>
 */
public interface CorpusSnapshot {

    /**
     * Retrieves a document by its identifier.
     *
     * @param id the document identifier
     * @return an Optional containing the document if present in this snapshot
     */
    Optional<Document> get(String id);

    /**
     * Returns whether this snapshot contains a document with the given id.
     *
     * @param id the document identifier
     * @return true if the document is present
     */
    boolean contains(String id);

    /**
     * Returns an iterable view over all documents captured in this snapshot.
     *
     * @return all documents at snapshot time
     */
    Iterable<Document> documents();

    /**
     * Returns the number of documents captured in this snapshot.
     *
     * @return document count at snapshot time
     */
    int size();

    /**
     * Returns the corpus statistics captured at snapshot time.
     *
     * @return frozen corpus statistics
     */
    CorpusStatistics statistics();
}
