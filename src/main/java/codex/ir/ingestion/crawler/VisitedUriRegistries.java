package codex.ir.ingestion.crawler;

import java.net.URI;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory for creating {@link VisitedUriRegistry} implementations.
 * @author jsanca & elo
 */
public class VisitedUriRegistries {

    private VisitedUriRegistries() { }

    /**
     * Creates an in-memory visited URI registry backed by a concurrent set.
     */
    public static VisitedUriRegistry inMemory () {
        return new InMemoryVisitedUriRegistry();
    }

    private static final class InMemoryVisitedUriRegistry implements VisitedUriRegistry {

        private final Set<URI> visitedUris = ConcurrentHashMap.newKeySet();

        @Override
        public boolean markVisited(final URI uri) {
            return this.visitedUris.add(uri);
        }

        @Override
        public boolean isVisited(final URI uri) {
            return this.visitedUris.contains(uri);
        }
    }
}
