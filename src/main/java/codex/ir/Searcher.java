package codex.ir;

import java.util.List;

/**
 * High level search API for the IR engine.
 *
 * A Searcher is responsible for:
 *  - parsing the user query
 *  - tokenizing and normalizing query terms
 *  - consulting the InvertedIndex
 *  - resolving document ids in the Corpus
 *  - returning matching documents
 * @author jsanca & elo
 */
public interface Searcher {

    /**
     * Executes a search query.
     *
     * @param query raw user query
     * @return list of matching documents
     */
    List<Document> search(String query);

    /**
     * Executes a search query but returns more information
     * @param query raw user query
     * @return list of matching search results
     */
    List<SearchResult> searchDetailed(String query);
}
