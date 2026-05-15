package codex.ir.canonicalizer;

import java.net.URI;

/**
 * Defines the contract for canonicalizing {@link URI} instances before they are
 * used by crawling and ingestion components.
 * <p>
 * Implementations may normalize scheme, host, path, query parameters, trailing
 * slashes, fragments, or any other URI parts that should not affect the logical
 * identity of a resource.
 * @author jsanca & elo
 */
public interface UriCanonicalizer {

    /**
     * Produces a canonical representation of the given URI.
     * <p>
     * The returned URI should represent the logical resource identity that the
     * crawler and indexer should treat as the source of truth.
     *
     * @param uri the input URI to canonicalize
     * @return the canonicalized URI
     */
    URI canonicalize(URI uri);
}
