package codex.apps.siteexporter;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

/**
 * Extracts asset references from an HTML document using Jsoup.
 *
 * <p>Discovered selectors and their attribute:</p>
 * <ul>
 *   <li>{@code img[src]} → {@link AssetType#IMAGE}</li>
 *   <li>{@code link[rel=stylesheet][href]} → {@link AssetType#STYLESHEET}</li>
 *   <li>{@code script[src]} → {@link AssetType#SCRIPT}</li>
 * </ul>
 *
 * <p>Only {@code http} and {@code https} URLs are returned. Relative URLs are
 * resolved against {@code pageUrl} via Jsoup's {@code absUrl()} mechanism.
 * References are returned in document order; within a single page each unique
 * URL appears at most once.</p>
 */
final class AssetReferenceExtractor {

    /**
     * @param html    raw HTML content of the page
     * @param pageUrl absolute URL of the page; used as base for relative URL resolution
     * @return document-ordered, deduplicated list of asset references found in the page
     */
    List<AssetReference> extract(final String html, final URI pageUrl) {
        Objects.requireNonNull(html, "html");
        Objects.requireNonNull(pageUrl, "pageUrl");

        final Document doc = Jsoup.parse(html, pageUrl.toString());
        final LinkedHashMap<URI, AssetType> seen = new LinkedHashMap<>();

        for (final Element el : doc.getAllElements()) {
            switch (el.tagName().toLowerCase()) {
                case "img" -> tryAdd(el.absUrl("src"), AssetType.IMAGE, seen);
                case "link" -> {
                    if (el.attr("rel").contains("stylesheet")) {
                        tryAdd(el.absUrl("href"), AssetType.STYLESHEET, seen);
                    }
                }
                case "script" -> tryAdd(el.absUrl("src"), AssetType.SCRIPT, seen);
                default -> { /* not an asset element */ }
            }
        }

        final List<AssetReference> refs = new ArrayList<>(seen.size());
        seen.forEach((uri, type) -> refs.add(new AssetReference(uri, type)));
        return refs;
    }

    private static void tryAdd(final String absUrl, final AssetType type,
            final LinkedHashMap<URI, AssetType> seen) {
        if (absUrl.isEmpty()) {
            return;
        }
        if (!absUrl.startsWith("http://") && !absUrl.startsWith("https://")) {
            return;
        }
        try {
            seen.putIfAbsent(new URI(absUrl), type);
        } catch (final URISyntaxException ignored) {
            // malformed URL — skip silently
        }
    }
}
