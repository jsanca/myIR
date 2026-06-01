package codex.ir.ingestion.crawler.internal.classifier;

import codex.ir.ingestion.crawler.classifier.UrlType;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.EnumSet;
import java.util.Set;

/**
 * Shared HTML signal helpers for page classifier implementations.
 */
final class HtmlSignals {

    static final Set<UrlType> PASS_THROUGH_TYPES = EnumSet.of(
            UrlType.ADMIN, UrlType.CART, UrlType.CHECKOUT, UrlType.ACCOUNT,
            UrlType.FEED, UrlType.ASSET, UrlType.SEARCH, UrlType.IGNORED
    );

    private HtmlSignals() {
    }

    /**
     * Returns {@code true} if any JSON-LD script block in the document
     * contains the given {@code @type} value.
     */
    static boolean hasJsonLdType(final Document doc, final String type) {
        for (final Element script : doc.select("script[type=\"application/ld+json\"]")) {
            final String json = script.html();
            if (json != null && json.contains("\"@type\"") && json.contains("\"" + type + "\"")) {
                return true;
            }
        }
        return false;
    }

    /** Returns the {@code og:type} meta content value, or {@code null} if absent. */
    static String ogType(final Document doc) {
        return metaContent(doc, "meta[property=og:type]");
    }

    /** Returns the {@code content} attribute of the first element matching {@code cssQuery}. */
    static String metaContent(final Document doc, final String cssQuery) {
        final Element el = doc.selectFirst(cssQuery);
        return el != null ? el.attr("content") : null;
    }

    /** Returns the first element matching {@code cssQuery}, or {@code null} if none. */
    static Element selectFirst(final Document doc, final String cssQuery) {
        return doc.selectFirst(cssQuery);
    }
}
