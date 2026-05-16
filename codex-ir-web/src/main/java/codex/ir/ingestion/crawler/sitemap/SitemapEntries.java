package codex.ir.ingestion.crawler.sitemap;

import java.util.List;

/**
 * Typed container for parsed sitemap content.
 *
 * <p>Separates URL entries (from urlset) from sitemap references
 * (from sitemapindex) for clarity.</p>
 */
record SitemapEntries(
        List<SitemapEntry.UrlEntry> urlEntries,
        List<SitemapEntry.SitemapRef> sitemapRefs
) {

    static final SitemapEntries EMPTY = new SitemapEntries(List.of(), List.of());

    boolean hasUrlEntries() {
        return !urlEntries.isEmpty();
    }

    boolean hasSitemapRefs() {
        return !sitemapRefs.isEmpty();
    }

    boolean isEmpty() {
        return urlEntries.isEmpty() && sitemapRefs.isEmpty();
    }
}
