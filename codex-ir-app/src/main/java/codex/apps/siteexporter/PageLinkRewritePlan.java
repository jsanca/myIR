package codex.apps.siteexporter;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Maps successfully-mirrored page URLs to their local HTML paths.
 *
 * <p>Only {@link MirrorStatus#SUCCESS} pages with a non-null {@code localHtmlPath}
 * are indexed. Pages that failed to write or were skipped are excluded — links
 * pointing to them will be left unchanged by the rewriter.</p>
 *
 * <p>{@link #localPath(URI)} returns {@code null} if the URL is not in the plan.</p>
 */
final class PageLinkRewritePlan {

    private final Map<URI, String> urlToLocalPath;

    private PageLinkRewritePlan(final Map<URI, String> urlToLocalPath) {
        this.urlToLocalPath = Map.copyOf(urlToLocalPath);
    }

    static PageLinkRewritePlan from(final MirrorManifest manifest) {
        Objects.requireNonNull(manifest, "manifest");
        final Map<URI, String> index = new HashMap<>();
        for (final MirroredPage page : manifest.pages()) {
            if (page.mirrorStatus() == MirrorStatus.SUCCESS && page.localHtmlPath() != null) {
                index.put(page.url(), page.localHtmlPath());
            }
        }
        return new PageLinkRewritePlan(index);
    }

    /** Returns the local HTML path for the given page URL, or {@code null} if not indexed. */
    String localPath(final URI url) {
        return urlToLocalPath.get(url);
    }
}
