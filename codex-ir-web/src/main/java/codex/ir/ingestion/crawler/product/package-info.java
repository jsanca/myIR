/**
 * Product discovery and extraction from web pages.
 * <p>
 * {@link codex.ir.ingestion.crawler.product.ProductDiscoverer} orchestrates
 * page classification and product extraction.
 * {@link codex.ir.ingestion.crawler.product.ProductCardExtractor} extracts
 * product summaries from listing pages.
 * {@link codex.ir.ingestion.crawler.product.ProductDetailExtractor} extracts
 * full product details from product pages.
 * Results are aggregated into
 * {@link codex.ir.ingestion.crawler.product.ProductDiscoveryReport} and
 * written via {@link codex.ir.ingestion.crawler.product.DiscoveryReportWriter}.
 * </p>
 */
package codex.ir.ingestion.crawler.product;
