Please create the first technical plan for a generic WordPress/WooCommerce crawler and information extractor.

Context:
We have finished the myIR stabilization phase. Now we want to start planning a crawler and deterministic information extractor for websites built with WordPress and WooCommerce.

Important:
Do not hardcode the design for a specific client website. The crawler/extractor should eventually work with this site or other WordPress/WooCommerce sites with similar structure.

You may use a representative WordPress/WooCommerce site as an example during analysis, but the architecture, naming, model, and extraction strategy must remain site-agnostic.

Goal:
Create a technical plan for a reusable WordPress/WooCommerce crawler and deterministic extractor.

Questions to answer:
1. Should discovery start from robots.txt/sitemap.xml or from root crawling?
2. Can product/catalog information generally be extracted deterministically with Jsoup?
3. What WordPress/WooCommerce conventions can we rely on?
4. Which parts must remain configurable because themes/plugins vary?
5. Is there any reason to use Playwright or an LLM/LangChain4j-style inference layer in the first version?
6. What is the smallest useful first implementation milestone?

Scope:

1. Discovery strategy
   Analyze a generic discovery flow:
    - robots.txt
    - sitemap.xml
    - sitemap indexes
    - WordPress-generated sitemaps
    - WooCommerce product/category URLs
    - homepage navigation
    - category/listing pages
    - product detail pages

   Recommend whether sitemap-first discovery should be the default.
   Also explain when root crawling is still useful as a fallback.

2. WordPress/WooCommerce detection
   Propose how to detect:
    - WordPress
    - WooCommerce
    - product pages
    - product category pages
    - listing/archive pages
    - informational pages
    - pages that should be ignored, such as cart, checkout, account, search, order tracking, login, admin, feeds, and query-param filters

   Consider signals such as:
    - URL patterns
    - HTML classes
    - meta tags
    - structured data
    - WordPress/WooCommerce CSS classes
    - JSON-LD
    - OpenGraph data
    - canonical links

3. Deterministic extraction strategy
   Propose a Jsoup-first extraction strategy based on:
    - semantic HTML
    - WooCommerce CSS classes
    - schema.org / JSON-LD Product data
    - OpenGraph tags
    - canonical URLs
    - lazy-loaded image attributes
    - product card/listing selectors
    - product detail selectors

   Explain which fields can usually be extracted deterministically:
    - product name
    - product URL
    - category path
    - price
    - sale price
    - currency
    - images
    - short description
    - long description
    - SKU
    - availability
    - rating
    - variations/options when available in HTML

4. Configuration model
   Propose how the extractor can remain generic while still supporting theme differences.

   Consider:
    - SiteProfile
    - ExtractorProfile
    - selector overrides
    - ignored URL patterns
    - category URL patterns
    - product URL patterns
    - currency normalization
    - image URL normalization
    - max crawl depth
    - max pages
    - rate limiting
    - user-agent
    - same-host policy

5. Proposed domain model
   Suggest small internal data types such as:
    - CrawledUrl
    - UrlType
    - SiteProfile
    - CrawlBoundary
    - DiscoveredLink
    - ParsedPage
    - PageClassification
    - ProductCard
    - ProductDetail
    - ProductImage
    - ProductPrice
    - ProductVariation
    - CategoryInfo
    - ExtractionResult
    - ProductProjection

   Keep these as proposed models only unless implementation is explicitly requested later.

6. Proposed pipeline
   Design a minimal reusable pipeline:

    - Read robots.txt
    - Discover sitemap URLs
    - Parse sitemap indexes/files
    - Normalize/canonicalize URLs
    - Classify URLs
    - Filter ignored URLs
    - Fetch selected sample pages
    - Parse HTML with Jsoup
    - Classify page type
    - Extract product cards from listing/archive pages
    - Extract product details from product pages
    - Normalize extracted data
    - Emit ProductProjection objects

   No persistence yet.
   No dotCMS integration yet.
   No LLM inference yet.
   No Playwright yet unless clearly justified.

7. Inference / LLM recommendation
   Explain whether LangChain4j or an LLM layer is needed in the first version.

   Preferred assumption:
    - Product/catalog extraction should be deterministic first.
    - LLM inference may be useful later for ambiguous or higher-level tasks.

   Discuss future LLM use cases:
    - inferring content blocks from arbitrary pages
    - mapping extracted content to CMS content types
    - detecting hero banners, promotional sections, FAQs, testimonials
    - generating SEO summaries
    - suggesting alt text
    - normalizing messy descriptions
    - classifying pages that do not follow WooCommerce conventions

8. Playwright recommendation
   Explain whether Playwright is needed in the first version.

   Preferred assumption:
    - Use Jsoup first.
    - Add Playwright only as fallback if product data is rendered client-side or hidden behind JavaScript interactions.

9. Crawl boundaries and safety
   Recommend default rules:
    - same host only
    - respect robots.txt
    - polite rate limiting
    - ignore admin/login/cart/checkout/account/order/search/feed URLs
    - avoid query-param explosion
    - canonicalize URLs
    - deduplicate URLs
    - limit first prototype by max pages/depth
    - avoid destructive or transactional URLs

10. First implementation milestone
    Recommend the smallest useful first milestone.

Suggested milestone:
- given a base URL
- read robots.txt
- find sitemap URLs
- parse sitemap URLs
- classify URLs into product, category/listing, page, ignored, unknown
- fetch a tiny sample:
    - homepage
    - one listing/category page
    - one product detail page
- extract ProductCard/ProductDetail fields deterministically
- print results to console or expose them through tests
- no persistence
- no dotCMS push
- no BFS crawler yet
- no LLM
- no Playwright

Constraints:
- Do not implement the crawler yet.
- Do not make the design specific to one client site.
- Do not hardcode syjleathers-specific names, categories, selectors, or URL paths.
- Do not add Playwright yet.
- Do not add LangChain4j yet.
- Do not integrate dotCMS yet.
- Do not add persistence yet.
- Do not refactor myIR core.
- Keep this as a technical plan.
- Keep documentation in English.
- Preserve the incremental/didactic nature of the project.

Expected output:
- Generic WordPress/WooCommerce discovery strategy
- WordPress/WooCommerce detection signals
- Deterministic extraction feasibility
- Configurability model
- Proposed domain model
- Proposed pipeline
- Crawl boundary rules
- Inference/LLM recommendation
- Playwright recommendation
- First implementation milestone
- Risks/unknowns