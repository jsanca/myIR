/**
 * Web crawling API.
 * <p>
 * {@link codex.ir.ingestion.crawler.WebPageFetcher} produces rich
 * {@link codex.ir.ingestion.WebPage} instances from URIs.
 * {@link codex.ir.ingestion.crawler.WebPageSourceStrategy} defines traversal
 * mechanisms (BFS site traversal or sitemap-based).
 * {@link codex.ir.ingestion.crawler.CrawlerRuntime} provides a shared runtime
 * facade with cached fetcher registries.
 * {@link codex.ir.ingestion.crawler.VisitedUriRegistry} tracks visited URIs
 * across a traversal session.
 * </p>
 */
package codex.ir.ingestion.crawler;
