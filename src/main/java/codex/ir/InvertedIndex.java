package codex.ir;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Abstraction representing an inverted index.
 *
 * An inverted index maps terms to the set of documents where they appear.
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
     * Adds a term occurrence for the given document.
     *
     * @param term the normalized term
     * @param documentId identifier of the document containing the term
     */
    void add(String term, String documentId);

    /**
     * Searches the index for a term.
     *
     * @param term normalized term
     * @return set of document identifiers containing the term
     */
    Set<String> search(String term);

    /**
     * Returns the full index representation.
     *
     * Useful for debugging and experimentation.
     */
    Map<String, Set<String>> asMap();
}


