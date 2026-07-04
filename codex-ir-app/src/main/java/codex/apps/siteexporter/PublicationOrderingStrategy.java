package codex.apps.siteexporter;

import java.util.List;

/**
 * Selects and orders the {@link MirroredPage} entries that {@link PublicationPipeline}
 * will render.
 *
 * <p>Implementations are responsible for filtering (e.g. excluding non-SUCCESS pages)
 * as well as ordering. The returned list is rendered in index order.</p>
 *
 * <p>The default implementation is {@link DiscoveredOrderPublicationOrderingStrategy}.</p>
 */
public interface PublicationOrderingStrategy {

    /**
     * Returns the ordered list of pages to render from {@code manifest}.
     *
     * @param manifest the mirror manifest; must not be {@code null}
     * @return ordered, filtered list of pages; never {@code null}
     */
    List<MirroredPage> order(MirrorManifest manifest);
}
