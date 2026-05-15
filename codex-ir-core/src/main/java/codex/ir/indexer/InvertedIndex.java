package codex.ir.indexer;

import java.util.*;

/**
 * Abstraction representing an inverted index.
 *
 * An inverted index maps terms to postings lists, where each posting
 * records the document identifier together with term-level metadata
 * such as frequency and positions.
 * Different implementations may exist depending on the storage strategy,
 * such as:
 *
 * - in‑memory indexes
 * - disk‑backed indexes
 * - distributed indexes
 * - compressed indexes
 */
public interface InvertedIndex {

    /**
     * Adds a term occurrence for the given document and position.
     *
     * @param term the normalized term
     * @param documentId identifier of the document containing the term
     * @param position zero-based token position within the document
     */
    void add(final String term, final String documentId, final int position);

    /**
     * Returns the postings list associated with a term.
     *
     * Each posting represents one document where the term appears,
     * together with metadata such as term frequency and positions.
     *
     * @param term normalized term
     * @return postings list for the term, or an empty list if absent
     */
    List<Posting> getPostings(final String term);

    /**
     * Returns the full index representation.
     *
     * The returned structure maps each normalized term to its postings list.
     * Useful for debugging and experimentation.
     */
    Map<String, List<Posting>> asMap();
}
