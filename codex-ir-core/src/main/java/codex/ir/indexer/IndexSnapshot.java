package codex.ir.indexer;

import java.util.List;
import java.util.Map;

/**
 * Immutable, point-in-time view of an inverted index captured after an ingestion round.
 *
 * <p>An {@code IndexSnapshot} is produced by {@link InvertedIndex#snapshot()} once all writes
 * for a given batch are complete. The snapshot captures every term's postings list at that
 * instant. Subsequent calls to {@link InvertedIndex#add} do not affect it.</p>
 *
 * <p>Read-path components — {@link codex.ir.ranking.Ranker} and
 * {@link codex.ir.search.SimpleSearcher} — consume an {@code IndexSnapshot} rather than the
 * live {@link InvertedIndex}. This decouples the write boundary (ingestion) from the read
 * boundary (search and ranking) and guarantees a consistent view of term statistics.</p>
 *
 * <p>Implementations must be safe to share across threads without external synchronization.</p>
 */
public interface IndexSnapshot {

    /**
     * Returns the postings list for a term in this snapshot.
     *
     * @param term normalized term
     * @return postings list for the term, or an empty list if absent
     */
    List<Posting> getPostings(String term);

    /**
     * Returns the full index captured by this snapshot as an unmodifiable map.
     *
     * @return map from term to its postings list
     */
    Map<String, List<Posting>> asMap();
}
