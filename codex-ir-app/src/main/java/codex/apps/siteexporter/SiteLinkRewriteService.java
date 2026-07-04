package codex.apps.siteexporter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Rewrites the links in all successfully-mirrored HTML pages so that the mirror
 * is locally navigable without a web server.
 *
 * <p>For each mirrored page with {@link MirrorStatus#SUCCESS}:</p>
 * <ol>
 *   <li>Reads the local HTML file from disk.</li>
 *   <li>Rewrites internal {@code a[href]} links to relative paths pointing to
 *       the corresponding local HTML files.</li>
 *   <li>Rewrites {@code img[src]}, {@code link[href]}, and {@code script[src]}
 *       references to the local downloaded asset files.</li>
 *   <li>Writes the rewritten HTML back to the same file.</li>
 * </ol>
 *
 * <p>Links to pages or assets that were not successfully mirrored/downloaded
 * are left unchanged.</p>
 */
public final class SiteLinkRewriteService {

    /**
     * Rewrites all mirrored HTML pages in place.
     *
     * @param outputDir      the mirror output directory; must exist
     * @param mirrorManifest manifest produced by {@link SiteMirrorService}
     * @param assetManifest  manifest produced by {@link SiteAssetService}
     * @return count of HTML files that were rewritten
     * @throws IOException if a file cannot be read or written
     */
    public int rewrite(final Path outputDir, final MirrorManifest mirrorManifest,
            final AssetManifest assetManifest) throws IOException {
        Objects.requireNonNull(outputDir, "outputDir");
        Objects.requireNonNull(mirrorManifest, "mirrorManifest");
        Objects.requireNonNull(assetManifest, "assetManifest");

        final PageLinkRewritePlan pagePlan = PageLinkRewritePlan.from(mirrorManifest);
        final AssetLinkRewritePlan assetPlan = AssetLinkRewritePlan.from(assetManifest);
        final HtmlLinkRewriter rewriter = new HtmlLinkRewriter();
        int rewritten = 0;

        for (final MirroredPage page : mirrorManifest.pages()) {
            if (page.mirrorStatus() != MirrorStatus.SUCCESS || page.localHtmlPath() == null) {
                continue;
            }
            final Path htmlFile = outputDir.resolve(page.localHtmlPath());
            if (!Files.exists(htmlFile)) {
                System.err.printf("[Rewrite][WARN] HTML file not found: %s%n", htmlFile);
                continue;
            }
            final String original = Files.readString(htmlFile);
            final String rewrittenHtml = rewriter.rewrite(
                    original, page.url(), page.localHtmlPath(), pagePlan, assetPlan);
            Files.writeString(htmlFile, rewrittenHtml);
            rewritten++;
            System.out.printf("[Rewrite] %s%n", htmlFile);
        }

        System.out.printf("[Rewrite] Done. %d page(s) rewritten.%n", rewritten);
        return rewritten;
    }
}
