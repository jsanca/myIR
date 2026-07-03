/**
 * Document ingestion pipeline.
 * <p>
 * Orchestrates the flow from data sources through mapping into the IR engine.
 * {@link codex.ir.ingestion.DocumentSource} produces items (e.g. web pages),
 * {@link codex.ir.ingestion.DocumentMapper} transforms them into
 * {@link codex.ir.Document} instances, and
 * {@link codex.ir.ingestion.DocumentIngestionService} coordinates the pipeline.
 * {@link codex.ir.ingestion.WebPage} represents a fetched web page with
 * metadata. {@link codex.ir.ingestion.WebCrawlingConfig} configures crawl
 * behavior (depth, concurrency, politeness).
 * </p>
 */
package codex.ir.ingestion;
