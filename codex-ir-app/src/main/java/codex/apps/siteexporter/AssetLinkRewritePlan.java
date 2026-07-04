package codex.apps.siteexporter;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Maps successfully-downloaded asset URLs to their local asset paths.
 *
 * <p>Only {@link AssetStatus#SUCCESS} assets with a non-null {@code localAssetPath}
 * are indexed. Failed or skipped assets are excluded — references to them will be
 * left unchanged by the rewriter.</p>
 *
 * <p>{@link #localPath(URI)} returns {@code null} if the URL is not in the plan.</p>
 */
final class AssetLinkRewritePlan {

    private final Map<URI, String> urlToLocalPath;

    private AssetLinkRewritePlan(final Map<URI, String> urlToLocalPath) {
        this.urlToLocalPath = Map.copyOf(urlToLocalPath);
    }

    static AssetLinkRewritePlan from(final AssetManifest manifest) {
        Objects.requireNonNull(manifest, "manifest");
        final Map<URI, String> index = new HashMap<>();
        for (final AssetMetadata asset : manifest.assets()) {
            if (asset.assetStatus() == AssetStatus.SUCCESS && asset.localAssetPath() != null) {
                index.put(asset.url(), asset.localAssetPath());
            }
        }
        return new AssetLinkRewritePlan(index);
    }

    /** Returns the local asset path for the given URL, or {@code null} if not indexed. */
    String localPath(final URI url) {
        return urlToLocalPath.get(url);
    }
}
