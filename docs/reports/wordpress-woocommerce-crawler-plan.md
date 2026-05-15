# WordPress/WooCommerce Crawler & Deterministic Extractor — Technical Plan

## Status

Proposed — design exploration. No implementation yet.

## Context

The myIR stabilization phase is complete. The existing ingestion pipeline (`DocumentSource → DocumentMapper → Indexer`) and BFS crawler (`SiteTraversalStrategy`) provide a solid foundation. This plan extends that infrastructure for WordPress/WooCommerce sites with a deterministic extraction layer.

Key constraint: the design must work across arbitrary WordPress/WooCommerce sites, not a single target. Configuration handles theme/plugin variance.

---

## 1. Discovery Strategy

**Recommendation: sitemap-first as default, BFS root-crawling as fallback.**

WordPress generates structured XML sitemaps automatically (since WP 5.5 via `wp-sitemap.xml`). WooCommerce extends them with product and category-specific sitemaps. Sitemap parsing yields product and category URLs directly — no need to crawl every page.

### Sitemap source priority

| # | URL pattern | Source | Content |
|---|------------|--------|---------|
| 1 | From `robots.txt` `Sitemap:` directive | Any SEO plugin | Varies |
| 2 | `/wp-sitemap.xml` | WordPress core (5.5+) | Index linking to post, page, product, category sitemaps |
| 3 | `/sitemap_index.xml` | Yoast SEO, Rank Math | Index of type-specific sitemaps |
| 4 | `/sitemap.xml` | Generic / older plugins | Single sitemap or index |
| 5 | `/product-sitemap.xml` | WooCommerce native | Product URLs only |
| 6 | `/wp-sitemap-posts-product-1.xml` | WordPress core + WooCommerce | Product URLs only |

### Sitemap XML parsing

Handle both `<sitemapindex>` (index of sitemaps) and `<urlset>` (URL list). Extract `<loc>` values, resolve relative URLs, normalize.

```xml
<!-- sitemap index -->
<sitemapindex xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
  <sitemap><loc>https://example.com/wp-sitemap-posts-product-1.xml</loc></sitemap>
  <sitemap><loc>https://example.com/wp-sitemap-taxonomies-product_cat-1.xml</loc></sitemap>
</sitemapindex>

<!-- url set -->
<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
  <url><loc>https://example.com/product/red-shoes/</loc></url>
</urlset>
```

### Fallback: BFS root crawl

When no sitemap is found, fall back to the existing `SiteTraversalStrategy`. The URL classifier (Section 2) identifies product and category pages as they are discovered. This path already exists in `WebPageSourceStrategies.siteTraversal()`.

### Discovery flow

```
given(baseUrl)
  │
  ├─► fetchRobotsTxt(baseUrl) → extract Sitemap: directives
  │
  ├─► discoverSitemaps(baseUrl, robotsTxt)
  │     ├─ Try each known sitemap path (HEAD → GET)
  │     ├─ Parse first successful response
  │     └─ Extract all <loc> entries
  │
  ├─► [if sitemap found]  → classifyEachUrl(extractedUrls)
  ├─► [if no sitemap]     → fallbackBfsCrawl(baseUrl) [reuse SiteTraversalStrategy]
  │
  └─► [output] List<ClassifiedUrl> (product, category, page, ignored)
```

---

## 2. WordPress/WooCommerce Detection

Detection is per-page — not site-level. A single site can mix WordPress pages with custom sections. Each fetched page is classified independently.

### 2a. WordPress detection

| Signal | Jsoup selector | Reliability |
|--------|---------------|:---:|
| Generator meta tag | `meta[name=generator][content*="WordPress"]` | High |
| Theme stylesheet link | `link[href*="wp-content/themes/"]` | High |
| API link in head | `link[rel="https://api.w.org/"]` | High |
| WP includes script | `script[src*="wp-includes/"]` | Medium |
| Body classes with `wp-` prefix | `body[class*="wp-"]` | Medium |
| Admin bar or admin links | `a[href*="wp-admin"]` | Low |

### 2b. WooCommerce detection

| Signal | Jsoup selector | Reliability |
|--------|---------------|:---:|
| Body class | `body.woocommerce`, `body.woocommerce-page` | High |
| Plugin assets | `link[href*="woocommerce"], script[src*="woocommerce"]` | High |
| JSON-LD `@type: Product` | `script[type="application/ld+json"]` → parse → check `@type` | High |
| WooCommerce breadcrumb | `.woocommerce-breadcrumb` | High |
| Price element | `.woocommerce-Price-amount` | Medium |

### 2c. Page type classification

| Type | URL signals (pattern match) | HTML signals (if page is fetched) |
|------|---------------------------|----------------------------------|
| **Product** | `/product/`, `/shop/`, `/produit/`, `/producto/`, `/tienda/`, `/store/` | `schema.org/Product` JSON-LD, `.single-product`, `.product_title`, `.price`, `.single_add_to_cart_button` |
| **Category** | `/product-category/`, `/categoria-producto/`, `product_cat=`, `/shop/?product_cat=` | `.woocommerce-loop-product__title`, `.products.columns-`, `.woocommerce-pagination` |
| **Page** | `/about/`, `/contact/`, `/blog/`, `/page/`, `/` | No WooCommerce product classes, standard `article` or `.page` |
| **Homepage** | `/`, empty path, no sub-path | Body class `.home`, `.woocommerce-shop` |
| **Ignored** | `/cart/`, `/checkout/`, `/my-account/`, `/wp-admin/`, `/wp-login/`, `/wp-json/`, `/feed/`, `/search/`, `/order-tracking/`, `/track-order/`, `/wishlist/`, `/compare/`, `?add-to-cart=`, `?filter_`, `?orderby=`, `?min_price=`, `?max_price=`, `/page/2/` (pagination beyond limit) | Any transactional or admin URL |

Classification is a cascade: URL pattern match first (fast, no fetch needed), HTML signal check second (more accurate, requires fetch).

---

## 3. Deterministic Extraction Strategy

**Decision: Jsoup-first.** WooCommerce product pages emit structured data (JSON-LD, OpenGraph, microdata) and consistent CSS classes. JavaScript execution is not required for the initial HTML payload in standard themes.

### 3a. Extraction cascade

For each field, try sources in priority order until a non-empty value is found:

```
tryJsonLd() → tryOpenGraph() → tryMetaTags() → tryCssSelectors()
```

### 3b. Product fields and their extraction sources

| Field | Priority 1 (JSON-LD) | Priority 2 (Meta/OG) | Priority 3 (CSS) |
|-------|---------------------|----------------------|------------------|
| **name** | `Product.name` | `meta[property="og:title"]` | `.product_title`, `h1.product_title` |
| **regularPrice** | `Product.offers.price` | `meta[property="product:price:amount"]` | `.price .woocommerce-Price-amount` (non-del/ins), `bdi` |
| **salePrice** | `Product.offers.price` (if `priceSpecification`) | — | `.price ins .woocommerce-Price-amount`, `.price del .woocommerce-Price-amount` |
| **currency** | `Product.offers.priceCurrency` | — | `.woocommerce-Price-currencySymbol`, site profile default |
| **sku** | `Product.sku` | — | `.sku`, `.product_meta .sku` |
| **availability** | `Product.offers.availability` | — | `.stock.in-stock`, `.stock.out-of-stock`, `.available-on-backorder` |
| **shortDescription** | `Product.description` (truncated) | `meta[name="description"]` | `.woocommerce-product-details__short-description` |
| **fullDescription** | `Product.description` | — | `#tab-description`, `.woocommerce-Tabs-panel--description` |
| **images** | `Product.image[]` | `meta[property="og:image"]` | `.woocommerce-product-gallery__image img`, `figure.woocommerce-product-gallery__wrapper img` |
| **categories** | `BreadcrumbList.itemListElement[]` | — | `.woocommerce-breadcrumb a` (excluding last) |
| **rating** | `Product.aggregateRating.ratingValue` | — | `.star-rating[aria-label]`, `.woocommerce-product-rating .star-rating` |
| **productUrl** | `Product.url` | `link[rel="canonical"]` | — |
| **brand** | `Product.brand.name` | — | — |
| **gtin** | `Product.gtin`, `Product.gtin13`, `Product.gtin12` | — | — |
| **weight** | `Product.weight.value` + `Product.weight.unitText` | — | `.product_weight` |
| **dimensions** | `Product.depth`, `Product.width`, `Product.height` | — | `.product_dimensions` |

### 3c. JSON-LD extraction

WooCommerce embeds `application/ld+json` scripts containing `Product`, `BreadcrumbList`, `WebSite`, and `Organization` nodes. A `JsonLdExtractor` component:

1. Selects all `script[type="application/ld+json"]` elements via Jsoup
2. Parses each as a JSON object
3. Handles `@graph` arrays (common in Yoast SEO — multiple `@type` nodes in one script)
4. Maps known `schema.org` types to typed extraction records
5. Resolves `@id` references between nodes

### 3d. Image URL normalization

WooCommerce themes often use lazy-loaded images:

```html
<img src="placeholder.jpg"
     data-src="https://example.com/wp-content/uploads/product-photo.jpg"
     data-large_image="https://example.com/wp-content/uploads/product-photo-1024x1024.jpg"
     srcset="... 300w, ... 600w, ... 1024w" />
```

Extraction priority: `data-large_image` → `data-src` → `srcset` (largest) → `src`. Resolve relative URLs against the page base URL. Strip WooCommerce resize parameters (`?resize=`, `-300x300`, `-scaled`).

### 3e. Price parsing

Currency symbols and formats vary. A `PriceParser`:

1. Strips HTML tags from price elements (Jsoop `.text()`)
2. Strips currency symbols using the site profile's `currencyCode` or detected symbol
3. Parses the remaining numeric string to `BigDecimal`
4. Distinguishes regular vs sale price by surrounding HTML (`<del>` / `<ins>` or JSON-LD `priceSpecification`)

### 3f. Feasibility summary

| Field | Deterministic? | Notes |
|-------|:---:|-------|
| name | Yes | Very reliable across all themes |
| regularPrice | Yes | JSON-LD or `.price` element always present |
| salePrice | Conditional | Only present when product is on sale |
| currency | Yes | JSON-LD or config default |
| sku | Usually | Some themes hide SKU or use custom fields |
| availability | Usually | Stock status is standard WooCommerce output |
| shortDescription | Usually | Standard WooCommerce excerpt |
| fullDescription | Usually | Standard WooCommerce tab content |
| images | Yes | Gallery is a core WooCommerce feature |
| categories | Yes | Breadcrumbs or JSON-LD `BreadcrumbList` |
| rating | Conditional | Only when reviews are enabled |
| variations | Conditional | Present for variable products; complex HTML structure |

---

## 4. Configuration Model

A `SiteProfile` makes the extractor generic. Most sites work with the built-in `WooCommerceDefault` profile. Theme-specific overrides are explicit.

### Proposed types (design sketch)

```
SiteProfile
  ├─ profileId       : String
  ├─ siteName        : String
  ├─ locale          : Locale
  ├─ currencyCode    : String (ISO 4217)
  ├─ urlPatterns     : UrlPatterns
  ├─ selectors       : SelectorOverrides (empty = WooCommerce defaults)
  ├─ ignoredPatterns : IgnoredPatterns
  └─ crawlPolicy     : CrawlPolicy

UrlPatterns
  ├─ productUrlRegex  : Pattern
  ├─ categoryUrlRegex : Pattern
  └─ pageUrlRegex     : Pattern

SelectorOverrides   → Map<FieldName, String> (e.g., PRODUCT_NAME → "h1.custom-title")
IgnoredPatterns     → List<Pattern> (path patterns + query param patterns)
CrawlPolicy
  ├─ maxProducts      : int (default 200)
  ├─ maxPages         : int (default 500)
  ├─ maxDepth         : int (default 3)
  ├─ delayMillis      : long (default 500)
  ├─ sameHostOnly     : boolean (default true)
  └─ respectRobotsTxt : boolean (default true)
```

### Built-in profiles

| Profile | Use case |
|---------|----------|
| `SiteProfiles.wooCommerceDefault()` | Standard WooCommerce with `/product/` and `/product-category/` paths |
| `SiteProfiles.wooCommerceSpanish()` | Spanish WooCommerce with `/producto/` and `/categoria-producto/` paths |
| `SiteProfiles.wooCommerceCustom(UrlPatterns)` | Builder for site-specific URL patterns |

---

## 5. Proposed Domain Model

Lightweight immutable records. No external dependencies.

```
CrawledUrl        → (URI url, UrlType type, int depth, URI discoveredFrom)
UrlType           → enum { PRODUCT, CATEGORY, PAGE, HOMEPAGE, IGNORED, UNKNOWN }
DiscoveredLink    → (URI url, URI sourcePage, String anchorText)
PageClassification → (URI url, UrlType type, boolean isWordPress, boolean isWooCommerce)

ProductImage      → (URI url, String altText, int displayOrder)
ProductPrice      → (BigDecimal amount, String currencyCode, boolean isSalePrice)
ProductVariation  → (String attributeName, String attributeValue, String sku, ProductPrice price)
CategoryInfo      → (String name, URI url, List<CategoryInfo> subCategories)

ProductCard       → (URI url, String name, ProductPrice price, URI thumbnailUrl)
                    // Extracted from category/listing pages (summary only)

ProductDetail     → (URI url, String name, String sku, String gtin,
                     ProductPrice regularPrice, ProductPrice salePrice,
                     String shortDescription, String fullDescription,
                     List<ProductImage> images, List<CategoryInfo> categories,
                     String availability, Double rating, Integer reviewCount,
                     List<ProductVariation> variations)
                    // Extracted from product detail pages (complete)

ExtractionResult  → (URI url, Optional<ProductDetail> detail, List<String> warnings)
ProductProjection → (ProductDetail detail, URI sourceUrl, Instant extractedAt)
SitemapEntry      → (URI loc, Optional<Instant> lastModified, Optional<String> changeFrequency)
RobotsTxt          → (List<URI> sitemapUris, long crawlDelayMillis, List<String> disallowedPaths)
```

---

## 6. Proposed Pipeline

Stages run sequentially. Each stage is independently testable with JUnit.

```
 1. RobotsParser
    └─ HTTP GET /robots.txt
       Parse Sitemap: directive, Crawl-Delay, Disallow rules
       Input:  URI baseUrl
       Output: RobotsTxt

 2. SitemapDiscovery
    └─ Try known sitemap paths in priority order (HEAD → GET)
       Parse XML: handle <sitemapindex> nesting, extract <url><loc>
       Input:  URI baseUrl, RobotsTxt
       Output: List<DiscoveredLink>

 3. FallbackDiscovery        [only when sitemap returns empty]
    └─ Reuse existing SiteTraversalStrategy (BFS from homepage)
       Input:  WebCrawlingConfig, URI baseUrl
       Output: List<WebPage> (via existing SiteTraversalStrategy)

 4. PageClassifier
    └─ For each URL or fetched page:
       Step A: Match URL patterns → fast classification (product, category, ignored)
       Step B: If uncertain and page is fetched, check HTML signals
       Input:  URI url, Optional<WebPage>
       Output: PageClassification

 5. UrlFilter
    └─ Drop UrlType.IGNORED
       Deduplicate by canonical URL (reuse existing VisitedUriRegistry)
       Respect maxProducts / maxPages limits from CrawlPolicy
       Input:  List<PageClassification>, SiteProfile
       Output: List<PageClassification> (filtered, deduplicated)

 6. PageFetcher
    └─ Reuse existing JsoupWebPageFetcher from WebPageFetchers
       Fetch product and category pages
       Input:  List<URI> (product + category URLs)
       Output: List<WebPage>

 7. ProductCardExtractor     [category/listing pages]
    └─ Jsoup selectors:
       .product, .product-card, .type-product, li.product
       Extract: name (.woocommerce-loop-product__title), price (.price), URL (a),
                thumbnail (img[data-src] or img[src])
       Input:  WebPage (category page)
       Output: List<ProductCard>

 8. ProductDetailExtractor   [product pages]
    └─ Extraction cascade (JSON-LD → OG → meta → CSS)
       Input:  WebPage (product page), SiteProfile
       Output: ProductDetail

 9. DataNormalizer
    └─ Price: strip symbols, parse BigDecimal, detect sale vs regular
       Images: resolve relative URLs, prefer full-size, strip resize params
       Categories: trim, deduplicate, filter empty
       Descriptions: strip leftover HTML tags, normalize whitespace
       Input:  ProductDetail
       Output: ProductDetail (normalized)

10. Emitter
    └─ Milestone 1: print ProductProjection to console
       Later: ProductProjection → Document.mapper → Indexer.index (myIR integration)
       Input:  List<ProductDetail>
       Output: void (console) or List<Document> (myIR integration)
```

---

## 7. LLM / LangChain4j Recommendation

**Not needed in the first version.** Product extraction from WooCommerce is deterministic — JSON-LD, meta tags, and CSS classes are reliably present in standard themes. LLM inference would add cost, latency, and non-determinism for no benefit.

### Future LLM use cases (when justified)

| Use case | When needed | Approach |
|----------|------------|----------|
| Extract from non-WooCommerce themes | CSS/JSON-LD extraction returns empty | LLM as fallback extractor behind same interface |
| Classify pages without WP signals | URL and HTML classification both uncertain | LLM page classifier as optional stage |
| Normalize messy descriptions | HTML descriptions contain noise, unescaped tags | LLM text cleaner |
| Map to target CMS content types | Integration with external CMS | LLM content-type classifier |
| Detect hero banners, promos, FAQs | Non-product content extraction needed | LLM content block detector |
| Generate SEO summaries / alt text | Content enrichment pipeline | LLM text generator |

**Integration pattern:** Each LLM capability is an optional stage behind the same interface as its deterministic counterpart. Example:

```java
interface ProductDetailExtractor {
    Optional<ProductDetail> extract(WebPage page, SiteProfile profile);
}

// Deterministic (default)
class CssProductDetailExtractor implements ProductDetailExtractor { ... }

// LLM fallback (only invoked when deterministic returns empty)
class LlmProductDetailExtractor implements ProductDetailExtractor { ... }

// Composite (tries deterministic first, then LLM)
class FallbackProductDetailExtractor implements ProductDetailExtractor { ... }
```

This keeps the extraction pipeline interface stable regardless of whether LLM is used.

---

## 8. Playwright Recommendation

**Not needed in the first version.** WooCommerce product data (prices, names, SKUs, images) is rendered server-side in standard WordPress themes. JSON-LD, meta tags, and CSS classes are available in the initial HTML response fetched via `JsoupWebPageFetcher`.

### When to add Playwright

| Scenario | Why Jsoup fails |
|----------|----------------|
| Infinite-scroll category pages | Products loaded via AJAX on scroll — HTML source has only first page |
| JS-rendered price variations | Variable product prices change via JavaScript when selecting options |
| Client-side product configurators | Product data assembled in the browser from API calls |
| Bot detection / anti-scraping | Sites require browser fingerprinting (canvas, WebGL, etc.) |
| Missing JSON-LD with JS-only rendering | Some themes render structured data client-side |

Playwright is already a dependency. The `PlaywrightWebPageFetcher` stub in `WebPageFetchers.java` would be implemented as a fallback behind the `WebPageFetcher` interface. The rest of the pipeline would not change.

---

## 9. Crawl Boundaries and Safety Rules

| Rule | Default | Source |
|------|---------|--------|
| Same host only | `true` | `CrawlPolicy.sameHostOnly` |
| Respect robots.txt `Disallow` | `true` | `CrawlPolicy.respectRobotsTxt` |
| Respect `Crawl-Delay` | `true` (floor 500ms) | Parsed from robots.txt |
| Skip admin URLs | Always | `IgnoredPatterns` (hardcoded default) |
| Skip cart / checkout / account | Always | `IgnoredPatterns` (hardcoded default) |
| Skip feeds and search | Always | `IgnoredPatterns` (hardcoded default) |
| Skip query-param filters (`?filter_*`, `?orderby=`, `?min_price=`) | Always | `IgnoredPatterns` (configurable) |
| Skip pagination beyond limit (`/page/3/` and higher) | Always | `CrawlPolicy` |
| Avoid `?add-to-cart=` URLs | Always | Hardcoded (transactional) |
| Canonicalize URLs | Always | Reuse `UriCanonicalizers.defaultWeb()` |
| Deduplicate by canonical URL | Always | Reuse `VisitedUriRegistry` |
| Max products extracted | 200 | `CrawlPolicy.maxProducts` |
| Max pages fetched | 500 | `CrawlPolicy.maxPages` |
| Max crawl depth (BFS fallback) | 3 | `CrawlPolicy.maxDepth` |
| Polite delay between requests | 500ms | `CrawlPolicy.delayMillis` |
| User-Agent identification | Configurable | `CrawlPolicy` or system property |

---

## 10. First Implementation Milestone

```
Milestone 1: Sitemap Discovery + Single Product Extraction
═══════════════════════════════════════════════════════════

Given: a base URL (e.g., https://example-shop.com)

  1. Fetch robots.txt → extract Sitemap: directives
  2. Try known sitemap paths in priority order
  3. Parse first available sitemap XML
  4. Extract product URLs from <urlset> entries
  5. Classify URLs into product, category, page, ignored
  6. Fetch ONE product detail page via JsoupWebPageFetcher (reuse existing)
  7. Extract product fields via cascade (JSON-LD → meta → CSS)
  8. Normalize prices, images, categories
  9. Print ProductProjection to console
 10. Verify with tests using a local HTML fixture (no network dependency)

Deliverables:
  - SitemapParser           (handles sitemapindex + urlset XML)
  - PageClassifier          (URL patterns + HTML signals)
  - JsonLdExtractor         (parses JSON-LD scripts, maps schema.org types)
  - ProductDetailExtractor  (extraction cascade)
  - PriceParser             (currency-aware price normalization)
  - ImageUrlNormalizer      (lazy-load + relative URL resolution)

NOT in milestone 1:
  - Category page ProductCard extraction
  - Multi-product crawl
  - BFS fallback (already exists, just not wired in)
  - myIR Document integration
  - Persistence
  - Playwright
  - LLM
  - Any dotCMS or external CMS push
```

### Milestone 2 (future)

Add category page extraction (ProductCard from listings), multi-product crawl from sitemap, and `ProductProjection → DocumentMapper → Indexer` integration so products are searchable in myIR.

### Milestone 3 (future)

Add BFS fallback wiring, `SiteProfile` configuration model, `SelectorOverrides` for non-standard themes, and test fixtures for 3+ real WooCommerce sites.

---

## 11. Fit with Existing myIR Architecture

The plan extends, not rewrites, the existing ingestion pipeline:

```
                                    THIS PLAN (new)
                                    ═══════════════
Sources.webPage(source)      →     SitemapParser + PageClassifier
  │                                  │
  ▼                                  ▼
WebPage (existing)            ←     JsoupWebPageFetcher (reuse)
  │                                  │
  ▼                                  ▼
Mappers.webPage() (existing)  ←     ProductDetailExtractor (new)
  │                                  │
  ▼                                  ▼
Document (existing)           ←     ProductProjection → Document mapper
  │
  ▼
Indexer (existing)

New components plug into the existing Source → Mapper → Indexer interface.
The BFS crawler, URI canonicalizer, HTTP fetcher, and VisitedUriRegistry
are all reused without modification.
```

### Components to build (new)

| Component | Package | Replaces / extends |
|-----------|---------|-------------------|
| `SitemapParser` | `ingestion.crawler.sitemap` | Fills the `SiteMapStrategy` stub |
| `PageClassifier` | `ingestion.crawler.classifier` | New — URL + HTML classification |
| `JsonLdExtractor` | `ingestion.crawler.extractor` | New — structured data parsing |
| `ProductDetailExtractor` | `ingestion.crawler.extractor` | New — extraction cascade |
| `ProductCardExtractor` | `ingestion.crawler.extractor` | New — listing page extraction |
| `PriceParser` | `ingestion.crawler.extractor` | New — currency-aware parsing |
| `SiteProfile` | `ingestion.crawler.config` | New — configuration model |

### Components to reuse (existing)

| Component | Package |
|-----------|---------|
| `WebPageFetcher` / `JsoupWebPageFetcher` | `ingestion.crawler` |
| `SiteTraversalStrategy` | `ingestion.crawler` |
| `UriCanonicalizers` | `canonicalizer` |
| `VisitedUriRegistry` | `ingestion.crawler` |
| `WebHttpFetcher` | `ingestion.crawler.fetcher` |
| `DocumentSource` / `DocumentMapper` / `Ingestors` | `ingestion` |
| `WebCrawlingConfig` | `ingestion` |

---

## 12. Risks and Unknowns

| Risk | Mitigation |
|------|-----------|
| Some sites disable sitemaps or return cached/stale XML | BFS fallback via existing `SiteTraversalStrategy` |
| Theme-specific CSS classes don't match WooCommerce defaults | `SelectorOverrides` in `SiteProfile` |
| JSON-LD is malformed or missing (custom themes) | CSS extraction cascade as fallback |
| Product variations stored as separate simple products | JSON-LD `Product.isSimilarTo` or `hasVariant` detection |
| Multi-currency sites with no JSON-LD currency signal | `SiteProfile.currencyCode` as explicit fallback |
| CDN or cache layers strip WordPress generator tags | Multiple detection signals, not just generator meta |
| Large sitemaps (50K+ URLs) exceed memory for full parse | Stream-based XML parsing with configurable limit |
| Spanish/FR/DE WooCommerce uses different URL slugs | `UrlPatterns` and `SiteProfiles.wooCommerceSpanish()` preset |

---

This plan is intentionally a design document. No implementation.
