/**
 * Sitemap and robots.txt parsing.
 * <p>
 * {@code SitemapParser} parses XML sitemaps (sitemaps.org protocol).
 * {@code RobotsParser} extracts sitemap directives from robots.txt.
 * {@code SitemapSiteTraversalStrategy} discovers URLs via sitemaps and
 * delegates fetching. {@code SitemapEntry} is a sealed interface for
 * URL entries and sitemap references.
 * This package is <b>internal</b> and not JPMS-exported.
 * </p>
 */
package codex.ir.ingestion.crawler.internal.sitemap;
