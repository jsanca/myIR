package codex.apps.siteexporter;

import java.util.Comparator;
import java.util.List;

/**
 * Orders pages by {@link MirroredPage#discoveredOrder()} ascending.
 *
 * <p>Only {@link MirrorStatus#SUCCESS} pages with a non-null
 * {@link MirroredPage#localHtmlPath()} are included.</p>
 */
public final class DiscoveredOrderPublicationOrderingStrategy implements PublicationOrderingStrategy {

    @Override
    public List<MirroredPage> order(final MirrorManifest manifest) {
        return manifest.pages().stream()
                .filter(p -> p.mirrorStatus() == MirrorStatus.SUCCESS && p.localHtmlPath() != null)
                .sorted(Comparator.comparingLong(MirroredPage::discoveredOrder))
                .toList();
    }
}
