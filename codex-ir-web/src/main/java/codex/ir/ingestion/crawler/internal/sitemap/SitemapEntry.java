package codex.ir.ingestion.crawler.internal.sitemap;

import java.net.URI;
import java.time.Instant;
import java.util.Optional;

/**
 * Represents a single entry in a parsed sitemap.
 *
 * <p>Two variants exist: {@link UrlEntry} for URLs discovered in a urlset,
 * and {@link SitemapRef} for nested sitemap references in a sitemapindex.</p>
 */
sealed interface SitemapEntry permits SitemapEntry.UrlEntry, SitemapEntry.SitemapRef {

    /**
     * An entry representing a page URL discovered in a {@code <urlset>}.
     */
    record UrlEntry(URI loc, Optional<Instant> lastModified) implements SitemapEntry {
    }

    /**
     * An entry referencing a nested sitemap in a {@code <sitemapindex>}.
     */
    record SitemapRef(URI loc, Optional<Instant> lastModified) implements SitemapEntry {
    }
}
