# codex-ir-web

Web crawling, ingestion, URI canonicalization, page classification, metadata extraction, and product discovery. Depends on `codex-ir-core` for the `Document` and `Indexer` types.

## JPMS Module

`codex.ir.web` — exports 7 packages (see below). Internal packages (`crawler/fetcher/`, `crawler/internal/`, `web/util/`) are not exported and are inaccessible to `codex.ir.app`.

## Exported Packages

| Package | Responsibility |
|---------|---------------|
| `codex.ir.canonicalizer` | URI normalization pipeline (fragment removal, lowercasing, default port removal, path normalization, query sorting) |
| `codex.ir.ingestion` | Ingestion pipeline: `DocumentSource`, `DocumentMapper`, `DocumentIngestionService`, `WebPage` model |
| `codex.ir.ingestion.crawler` | Crawler API: `WebPageFetcher`, `WebPageSourceStrategy`, `CrawlerRuntime`, `VisitedUriRegistry` |
| `codex.ir.ingestion.crawler.classifier` | URL and page classification: `UrlClassifier`, `PageClassifier`, `UrlType` enum |
| `codex.ir.ingestion.crawler.filter` | Composable URL filter predicates |
| `codex.ir.ingestion.crawler.metadata` | HTML metadata extraction: OpenGraph, Twitter Cards, JSON-LD, headings, robots directives |
| `codex.ir.ingestion.crawler.product` | Product discovery: card extraction, detail extraction, discovery reports, JSON output |

## Internal Packages

| Package | Responsibility |
|---------|---------------|
| `codex.ir.ingestion.crawler.fetcher` | Low-level HTTP fetching via JDK `HttpClient` (`WebHttpFetcher`, `WebHttpResponse`) |
| `codex.ir.ingestion.crawler.internal.classifier` | Backing implementations: `JsoupGenericPageClassifier`, `WordPressWooCommercePageClassifier`, `HtmlSignals` |
| `codex.ir.ingestion.crawler.internal.sitemap` | Sitemap and robots.txt parsing (`SitemapParser`, `RobotsParser`, `SitemapSiteTraversalStrategy`) |
| `codex.ir.ingestion.crawler.internal.traversal` | BFS site traversal (`SiteTraversalStrategy`, `SeededWebPageTraversal`) |
| `codex.ir.ingestion.crawler.internal.product` | Internal extraction logic: JSON-LD extraction, price parsing, image resolution, extraction warnings |
| `codex.ir.ingestion.crawler.internal.metadata` | Contributor chain: `MetaTagExtractor`, `OpenGraphExtractor`, `TwitterCardExtractor`, `JsonLdBlockExtractor`, `HeadingExtractor`, `RobotsMetaExtractor` |
| `codex.ir.ingestion.crawler.internal.text` | `HtmlTextDecoder` — HTML entity decoding including double-encoded values |
| `codex.ir.web.util` | HTTP and URI helpers (`HttpUtil`, `UriUtil`) |

## Key Features

- **Jsoup-based static HTML fetching** — default fetcher for crawling
- **Playwright-based dynamic rendering** — available but requires `npx playwright install` before first use
- **Sitemap traversal** — discover URLs from robots.txt sitemap directives
- **BFS site traversal** — configurable depth, concurrency, and politeness delays
- **WordPress/WooCommerce detection** — classifier detects CMS-specific HTML patterns
- **JSON-LD product extraction** — parses structured product data from script blocks
- **Product discovery reports** — configurable output to console or JSON files

## Crawling Configuration

`WebCrawlingConfig` supports:

| Parameter | Default | Description |
|-----------|---------|-------------|
| `maxDepth` | 3 | Maximum link-following depth |
| `maxPages` | 100 | Maximum pages to crawl |
| `concurrentFetches` | 4 | Parallel fetch concurrency |
| `politenessDelayMs` | 1000 | Delay between requests to same host |
| `maxRetries` | 2 | Retry count for failed fetches |
| `timeoutMs` | 10000 | HTTP request timeout |

## Resources

- `src/test/resources/fixtures/woocommerce/product.html` — test fixture for WooCommerce product extraction

## Prerequisites

For any test that exercises `WebPageFetcher` (including Playwright-based tests), run once:

```shell
npx playwright install
```

## Common Commands

```shell
# Build
mvn compile -pl codex-ir-web

# All web tests
mvn test -pl codex-ir-web

# Single test
mvn test -pl codex-ir-web -Dtest=codex.ir.canonicalizer.UriCanonicalizersTest
```
