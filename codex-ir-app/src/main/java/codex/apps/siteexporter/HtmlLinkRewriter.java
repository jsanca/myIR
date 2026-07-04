package codex.apps.siteexporter;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Function;

/**
 * Rewrites the links in a single HTML document so that it is locally navigable.
 *
 * <p>Rewriting rules:</p>
 * <ul>
 *   <li>{@code a[href]} — internal links whose absolute URL appears in
 *       {@link PageLinkRewritePlan} are rewritten to relative paths pointing to
 *       the local HTML file. External links and links to unmirrored pages are
 *       left unchanged.</li>
 *   <li>{@code img[src]}, {@code link[rel=stylesheet][href]}, {@code script[src]}
 *       — references whose absolute URL appears in {@link AssetLinkRewritePlan}
 *       are rewritten to relative paths pointing to the local asset file.
 *       References to assets that were not downloaded are left unchanged.</li>
 * </ul>
 *
 * <p>The output is produced by Jsoup with {@code prettyPrint(false)} to minimize
 * whitespace changes to the original HTML.</p>
 */
final class HtmlLinkRewriter {

    /**
     * @param html              raw HTML of the page to rewrite
     * @param pageUrl           absolute URL of the page; used for relative URL resolution
     * @param pageLocalHtmlPath path of the HTML file relative to the mirror output directory
     *                          (e.g. {@code section/page/index.html})
     * @param pagePlan          index of mirrored page URLs → local HTML paths
     * @param assetPlan         index of downloaded asset URLs → local asset paths
     * @return rewritten HTML string
     */
    String rewrite(final String html, final URI pageUrl, final String pageLocalHtmlPath,
            final PageLinkRewritePlan pagePlan, final AssetLinkRewritePlan assetPlan) {
        Objects.requireNonNull(html, "html");
        Objects.requireNonNull(pageUrl, "pageUrl");
        Objects.requireNonNull(pageLocalHtmlPath, "pageLocalHtmlPath");
        Objects.requireNonNull(pagePlan, "pagePlan");
        Objects.requireNonNull(assetPlan, "assetPlan");

        final Document doc = Jsoup.parse(html, pageUrl.toString());
        doc.outputSettings().prettyPrint(false);

        for (final Element el : doc.select("a[href]")) {
            rewriteAttr(el, "href", pageLocalHtmlPath, pagePlan::localPath);
        }
        for (final Element el : doc.select("img[src]")) {
            rewriteAttr(el, "src", pageLocalHtmlPath, assetPlan::localPath);
        }
        for (final Element el : doc.select("link[rel=stylesheet]")) {
            rewriteAttr(el, "href", pageLocalHtmlPath, assetPlan::localPath);
        }
        for (final Element el : doc.select("script[src]")) {
            rewriteAttr(el, "src", pageLocalHtmlPath, assetPlan::localPath);
        }

        return doc.outerHtml();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static void rewriteAttr(final Element el, final String attr,
            final String pageLocalHtmlPath, final Function<URI, String> lookup) {
        final String absUrl = el.absUrl(attr);
        if (absUrl.isEmpty()) {
            return;
        }
        if (!absUrl.startsWith("http://") && !absUrl.startsWith("https://")) {
            return;
        }
        final URI target;
        try {
            target = new URI(absUrl);
        } catch (final URISyntaxException ignored) {
            return;
        }
        final String targetLocalPath = lookup.apply(target);
        if (targetLocalPath == null) {
            return;
        }
        el.attr(attr, computeRelativePath(pageLocalHtmlPath, targetLocalPath));
    }

    /**
     * Computes the relative path from the directory containing {@code fromLocalPath}
     * to {@code toLocalPath}, using forward slashes.
     *
     * <p>Both paths are relative to the same root (the mirror output directory).</p>
     *
     * <p>Examples:</p>
     * <ul>
     *   <li>{@code from="index.html"}, {@code to="about/index.html"} → {@code "about/index.html"}</li>
     *   <li>{@code from="section/page/index.html"}, {@code to="about/index.html"} → {@code "../../about/index.html"}</li>
     *   <li>{@code from="section/page/index.html"}, {@code to="assets/img/logo.png"} → {@code "../../assets/img/logo.png"}</li>
     * </ul>
     */
    static String computeRelativePath(final String fromLocalPath, final String toLocalPath) {
        Path fromDir = Path.of(fromLocalPath).getParent();
        if (fromDir == null) {
            fromDir = Path.of("");
        }
        return fromDir.relativize(Path.of(toLocalPath)).toString().replace('\\', '/');
    }
}
