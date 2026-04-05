package codex.ir.ingestion.crawler;

import java.net.URI;

/**
 * Registry of visited URIs for the lifetime of a traversal session.
 * @author jsanca & elo
 */
public interface VisitedUriRegistry {

    /**
     * Marks the given URI as visited.
     *
     * @param uri URI to register
     * @return true if the URI was marked for the first time
     */
    boolean markVisited(URI uri);

    /**
     * Returns whether the given URI was already visited.
     *
     * @param uri URI to check
     * @return true if the URI is already known as visited
     */
    boolean isVisited(URI uri);
}
