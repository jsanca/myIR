/**
 * Low-level HTTP transport abstraction.
 * <p>
 * {@link codex.ir.ingestion.crawler.fetcher.WebHttpFetcher} provides a
 * lightweight HTTP-only fetching interface backed by JDK {@code HttpClient}.
 * {@link codex.ir.ingestion.crawler.fetcher.WebHttpResponse} captures the
 * response (status, body, headers).
 * This package is <b>internal</b> and not JPMS-exported.
 * </p>
 */
package codex.ir.ingestion.crawler.fetcher;
