---
type: Engineering Log Entry
title: "myIR State of the Art — September 2026"
description: "Evidence-based assessment of myIR as it exists today: IR pipeline, capabilities, tests, build, web extraction surface, documentation landscape, and roadmap alignment."
tags: [assessment, state-of-the-art, core, web, app]
timestamp: 2026-09-04T00:00:00Z
ckf_version: "0.1"
ckf_status: completed
ckf_scope: cross-cutting
ckf_owner: project
---

# myIR State of the Art — September 2026

## 1. Executive Summary

myIR is a working, in-memory information retrieval engine with a functional web crawling and extraction layer bolted alongside it. As of September 2026:

- The **core IR pipeline** (tokenize → normalize → index → rank → search) is complete, tested, and supports both lexical (BM25/TF-IDF) and sparse vector (cosine similarity) retrieval.
- **Field-aware indexing and ranking** (IR-3, IR-4) were the most recently completed deliverables. Field-level postings are built and stored; field-weight boosting is applied at query time via `RankingContext`. All 197 core tests pass.
- The **web module** implements HTTP crawling, BFS and sitemap traversal, URL/page classification, product extraction (JSON-LD + HTML heuristics), and page metadata extraction. These are functional but carry WordPress/WooCommerce-centric defaults and a Playwright stub that does nothing. All 466 web tests pass.
- The **site exporter** (codex-ir-app) produces PDF, Markdown, and EPUB from a crawled site. It does not connect to the IR engine. All 359 app tests pass.
- **Total test suite: 1,022 tests, 0 failures, BUILD SUCCESS** (Java 25.0.2 / Maven 3.9.15).
- Score explanation, evaluation harness, dense vector retrieval, phrase/proximity search, and n-grams are not implemented. Positional data is stored but not used in query evaluation.
- Documentation is well-maintained for core IR (CKF logs through IR-4). Web extraction has no CKF engineering logs at all. Several legacy documents are stale.

The system is a sound experimental IR platform, not a production system. The next logical step is choosing one of: score explanation (IR-5), an evaluation harness (IR-6), or addressing the absence of the IR engine from the site-exporter flow.

---

## 2. Repository / Module Map

### 2.1 Maven / JPMS module structure

```
myIR (parent pom — reactor + BOM)
├── codex-ir-core   (JPMS: codex.ir.core)   — IR engine
├── codex-ir-web    (JPMS: codex.ir.web)    — web crawling, extraction
└── codex-ir-app    (JPMS: codex.ir.app)    — entry points, site exporter
```

Dependency direction: `app → web → core`. JPMS module boundaries are holding. Internal packages in `codex.ir.web` (`crawler/internal/`, `crawler/fetcher/`, `web/util/`) are unexported and inaccessible to `app`.

### 2.2 codex-ir-core packages

| Package | Responsibility | Key types |
|---|---|---|
| `codex.ir` | Root domain type | `Document`, `DocumentMetadata` |
| `corpus/` | Document store + statistics | `Corpus`/`Corpora`, `CorpusStatistics`, `CorpusSnapshot` |
| `corpus/vector/` | Term-to-integer ID mapping | `Vocabulary`/`Vocabularies` |
| `indexer/` | Full indexing pipeline | `InvertedIndex`/`InvertedIndexes`, `Indexers`, `PipelineIndexer`, `BatchPipelineIndexer`, `PreprocessedDocument`, `FieldAnalyzedDocument`, `FieldTokenSequence` |
| `ranking/` | Scoring models + field boosting | `Ranker`/`Rankers`, `FieldWeights`, `RankingContext` |
| `search/` | Query evaluation + result assembly | `Searcher`/`Searchers`, `SimpleSearcher`, `VectorSearcher` |
| `normalizer/` | Stop-word removal, lowercasing, accent folding | `Normalizer`/`Normalizers` |
| `tokenizer/` | Whitespace tokenization | `Tokenizer`/`Tokenizers` |
| `vector/` | Sparse vector types, similarity | `SparseDocumentVector`, `Vectorizer`, `Similarity`/`Similarities` |
| `vector/store/` | In-memory vector persistence | `DocumentVectorStore`/`VectorStores` |
| `weight/` | TF and TF-IDF term weighting | `DocumentWeighter`/`Weighters` |
| `concurrent/` | Virtual thread helpers, debounce | `VTExecutor`/`VTExecutors`, `Debouncer` |
| `util/` | IDF/TF math helpers | `TermWeightingUtils` (not exported) |

### 2.3 codex-ir-web packages (exported)

| Package | Responsibility |
|---|---|
| `ingestion/` | Bridge from `DocumentSource<T>` to core `Indexer` |
| `ingestion/crawler/` | `WebPageFetcher`, `WebPageSourceStrategy`, `WebCrawlerRuntime` |
| `ingestion/crawler/classifier/` | URL and page type classification |
| `ingestion/crawler/filter/` | Composable URL predicate set |
| `ingestion/crawler/metadata/` | Page metadata extraction (title, OG, headings, JSON-LD) |
| `ingestion/crawler/product/` | Product detail/card extraction, discovery pipeline |
| `canonicalizer/` | URI normalization |

### 2.4 codex-ir-app packages

| Package | Responsibility |
|---|---|
| `codex.scraper` | `Main` (web crawl demo), `DiscoveryRunner`, `QuickDiscoveryRunner` |
| `codex.apps.siteexporter` | `SiteExporterCommand`, full crawl-to-publication pipeline |

### 2.5 Intentional vs. blurred boundaries

**Intentional and well-maintained:**
- `codex.ir.core` has zero web/HTTP concepts. The `VTExecutor` in core is never called from core — it exists there for web to depend on, which is an acceptable design.
- JPMS `module-info.java` files enforce the public/internal distinction.

**Blurred or concerns:**
- `UrlClassifiers.wordpressWooCommerceDefaultWeb()` is exported as the "default web" classifier but embeds WooCommerce/WordPress multilingual path rules. New callers inherit these opinionated defaults silently.
- Default sitemap paths in `SitemapSiteTraversalStrategy` include `/wp-sitemap.xml`, `/wp-sitemap-posts-product-1.xml` — WordPress-specific assumptions in a reusable module.
- `QuickDiscoveryRunner` hard-codes `syjleathers.com` URLs — a client-specific dev shortcut in the application layer (acceptable but worth noting).
- `SiteExporterCommand` has no connection to the core IR engine. The site-exporter and IR pipeline are currently separate application concerns with no integration path.

---

## 3. Current IR Pipeline

### 3.1 End-to-end flow (lexical path)

```
Document (id, rawContent | fields, metadata)
  │
  ▼ DocumentPreprocessor
  │   resolveContent(): fields? → join non-blank field values : rawContent
  │   tokenize → normalize (stop-words, lowercase, trim) → token list
  │   analyzeFields(): per-field token sequences for field provenance
  │   writes: normalizedContent, termFrequencies, length, uniqueTerms back to Document
  │
  ▼ PreprocessedDocument / FieldAnalyzedDocument (artifacts)
  │
  ├─ LexicalIndexer
  │   corpus.add(document)
  │   for each token+position: index.add(term, docId, position)
  │   for each field token:     index.addFieldOccurrence(term, docId, fieldName)
  │
  └─ (optional) VectorIndexer
       documentWeighter.weigh(corpusSnapshot, document) → Map<term, weight>
       vectorizer.vectorize(docId, weights) → SparseDocumentVector
       vectorStore.save(vector)

Query
  │
  ▼ SimpleSearcher.searchDetailed(query)
  │   tokenize(query) → normalize each token
  │   for each term: invertedIndex.getPostings(term)
  │   union docIds; accumulate ranker.score(term, posting, rankingContext)
  │   resolve docId → Document from corpusSnapshot
  │   sort descending by score
  │
  ▼ List<SearchResult> (docId, document, score, matchedTerms)
```

### 3.2 Vector path

```
Query
  │
  ▼ VectorSearcher.searchDetailed(query)
  │   preprocess(query) → throwaway Document
  │   documentWeighter.weigh(corpus, queryDoc) → term weights
  │   vectorizer.vectorize(queryId, weights) → SparseDocumentVector
  │   iterate ALL stored vectors (linear scan, O(N))
  │   SparseCosineSimilarity.computeSimilarity(queryVec, docVec)
  │   filter by threshold; resolve docId → Document
  │   matched terms: vocabulary.getTerm(dimension) for contributing dimensions
  │
  ▼ List<SimilarityResult> (docId, document, score, matchedTerms)
```

### 3.3 Field-aware indexing detail

When a `Document.fields` map is non-empty:
1. `DocumentPreprocessor` aggregates all non-blank field values (field names discarded) as the single content stream. This is the whole-document normalized content used for `Corpus`, `InvertedIndex`, and `VectorIndexer`.
2. `analyzeFields()` additionally tokenizes+normalizes each field independently, producing one `FieldTokenSequence` per field.
3. `LexicalIndexer` stores field-level occurrence counts in `Posting.fieldFrequencies(Map<String,Integer>)` via `InvertedIndex.addFieldOccurrence()`.
4. At query time, `Ranker.score(term, posting, RankingContext)` computes a weighted boost: `boostFactor = Σ(fieldFreq[f] × weight[f]) / Σ(fieldFreq[f])`. Final score = `base × boostFactor`.
5. **Vector indexing does NOT participate in field-aware processing** — `VectorIndexer` always receives the bare `Document`.

### 3.4 Capability status by pipeline stage

| Stage | Status |
|---|---|
| Whitespace tokenization | Implemented and wired |
| Lowercase / trim / accent folding normalization | Implemented and wired |
| English stop-word removal | Implemented and wired |
| Spanish stop-word removal | Implemented and wired |
| Stemming | **Not implemented** |
| Character n-grams / sub-word tokenization | **Not implemented** |
| Lexical (inverted index) | Implemented and wired |
| Positional postings | Implemented and stored; **not used at query time** |
| Field-aware postings | Implemented and wired (IR-3) |
| Field-weight boosting (lexical) | Implemented and wired (IR-4) |
| Field-aware vector indexing | **Not implemented** |
| Batch indexing | Implemented and wired |
| Snapshot / read-write isolation | Implemented and wired |
| BM25 ranking | Implemented and wired |
| TF-IDF ranking | Implemented and wired |
| Binary ranking | Implemented and wired |
| BM25 parameter tuning (k1, b) | Implemented in class; **not exposed via public factory** |
| Sparse vector retrieval (cosine) | Implemented; **linear scan only** |
| Score explanation / trace | **Not implemented** |
| Phrase / proximity search | **Not implemented** (positions stored) |
| Boolean retrieval (AND/OR/NOT) | **Not implemented** (union semantics only) |
| Dense vector / semantic retrieval | **Not implemented** |
| Approximate nearest neighbor | **Not implemented** |
| Evaluation harness | **Not implemented** |

---

## 4. Capability Matrix

| Capability | Implemented | Wired End-to-End | Tested |
|---|---|---|---|
| Ingest documents (rawContent) | ✓ | ✓ | ✓ |
| Ingest documents (structured fields) | ✓ | ✓ | ✓ |
| Lexical search (BM25) | ✓ | ✓ | ✓ |
| Lexical search (TF-IDF) | ✓ | ✓ | ✓ |
| Lexical search (binary) | ✓ | ✓ | ✓ |
| Field-weight boosted lexical search | ✓ | ✓ | ✓ |
| Sparse vector search | ✓ | ✓ | ✓ |
| Batch indexing with consistent IDF | ✓ | ✓ | ✓ |
| Snapshot read isolation | ✓ | ✓ | ✓ |
| English/Spanish normalization | ✓ | ✓ | ✓ |
| Stemming | ✗ | ✗ | ✗ |
| Phrase/proximity search | partial (positions stored) | ✗ | ✗ |
| Boolean retrieval (AND/NOT) | ✗ | ✗ | ✗ |
| Score explanation | ✗ | ✗ | ✗ |
| Field-aware vector ranking | ✗ | ✗ | ✗ |
| BM25 k1/b parameter tuning | partial (class only) | ✗ | ✗ |
| Dense/semantic vector retrieval | ✗ | ✗ | ✗ |
| Evaluation / quality measurement | ✗ | ✗ | ✗ |
| Web crawling (HTTP, BFS) | ✓ | ✓ | ✓ (stub HTTP) |
| Web crawling (JavaScript / Playwright) | stub | ✗ | ✗ |
| Sitemap traversal | ✓ | ✓ | ✓ |
| URL/page classification | ✓ | ✓ | ✓ |
| Product extraction (JSON-LD + HTML) | ✓ | ✓ | ✓ |
| Page metadata extraction | ✓ | ✓ | ✓ |
| Site export (PDF) | ✓ | ✓ | ✓ |
| Site export (Markdown) | ✓ | ✓ | ✓ |
| Site export (EPUB) | ✓ | ✓ | partial |
| IR engine + site export integration | ✗ | ✗ | ✗ |

---

## 5. Index & Storage Model

### 5.1 InvertedIndex

**Contract** (`InvertedIndex` interface):
- `void add(String term, String documentId, int position)` — write
- `default void addFieldOccurrence(String term, String documentId, String fieldName)` — field write (no-op default)
- `List<Posting> getPostings(String term)` — read
- `Map<String, List<Posting>> asMap()` — full dump
- `IndexSnapshot snapshot()` — immutable copy

**One implementation:** `InMemoryInvertedIndex` (package-private inner class of `InvertedIndexes`).

**Storage structure:** `ConcurrentHashMap<String, Posting.Accumulator>`. Each `Posting.Accumulator` holds:
- `ConcurrentHashMap<String, List<Integer>> positionsByDocument` — term positions per document
- `ConcurrentHashMap<String, Map<String,Integer>> fieldFreqsByDocument` — field occurrence counts per document

**`Posting` record:** `documentId`, `termFrequency` (positions.size()), `positions` (List<Integer>), `fieldFrequencies` (Map<String,Integer>). Positions are fully stored for every term occurrence.

**`IndexSnapshot` / `InMemoryIndexSnapshot`:** deep copy of `asMap()` at snapshot time. Immutable after creation.

**Extensibility:** the interface is minimal and clean enough to support a future disk-backed or distributed implementation, but no such implementation exists or is in progress.

### 5.2 Corpus

**One implementation:** `InMemoryCorpus` (`ConcurrentHashMap<String, Document>`). Two statistics refresh modes:
- `EAGER` (default): recompute `CorpusStatistics` synchronously on every `add`
- `DEBOUNCED`: coalesce refreshes with a 250ms window via `Debouncer`

`CorpusStatistics` carries: `documentCount`, `totalDocumentLength`, `averageDocumentLength`, `version`. IDF is not stored — rankers compute it per-query from corpus size and posting list length.

**`CorpusSnapshot`:** `Map.copyOf` of the document map plus frozen statistics.

### 5.3 Vector store

**One implementation:** `InMemoryDocumentVectorStore` (`ConcurrentHashMap<String, SparseDocumentVector>`). `SparseDocumentVector` stores term-ID-to-weight sparse map plus precomputed L2 norm. No approximate index structure. Query evaluation is O(N) linear scan over all stored vectors.

### 5.4 Vocabulary

**One implementation:** `InMemoryVocabulary` — bidirectional `ConcurrentHashMap<String,Integer>` (term→id) + `ArrayList<String>` (id→term). Sequential, monotonically increasing IDs. `ReentrantLock` on write path. IDs are stable within a process run but not persisted.

### 5.5 Persistence policy

All structures are in-memory by design (ADR-003). No disk-backed, distributed, or compressed implementation exists or is planned. State is lost on restart.

---

## 6. Web / Extraction Surface

### 6.1 Fetcher stack

| Layer | Implementation | Notes |
|---|---|---|
| `WebHttpFetcher` (HTTP) | `JdkWebHttpFetcher` — `java.net.http.HttpClient` | 10s timeout, NORMAL redirect. `close()` has inverted null-check bug — never actually closes. VT executor not shut down (marked `// todo`). |
| `WebPageFetcher` (HTML) | `JsoupWebPageFetcher` | Parses HTML, extracts `a[href]` links, filters javascript:/mailto:/tel: |
| `WebPageFetcher` (dynamic) | `PlaywrightWebPageFetcher` | Stub — returns `Optional.empty()` unconditionally |

### 6.2 Traversal

Two production strategies:
- **BFS link-following** (`SiteTraversalStrategy`): `LinkedBlockingQueue` frontier, virtual threads (`VTExecutor`), respects `maxDepth`/`maxPages`/`sameDomainOnly`/`disallowedPaths`/per-request delay.
- **Sitemap-driven** (`SitemapSiteTraversalStrategy`): discovers sitemaps via robots.txt + fallback paths (including WP-specific paths); processes `<sitemapindex>` recursively; applies URL classification + filtering before accepting pages.

### 6.3 Classification

- **URL-level:** `UrlClassifiers.wordpressWooCommerceDefaultWeb()` — 14 `UrlType` values including product, category, blog, cart, checkout, admin, asset, ignored. WooCommerce/multilingual slug rules are embedded in the "default" classifier.
- **Page-level:** `PageClassifiers.jsoupDefault()` (generic HTML signals) and `PageClassifiers.wordpressWooCommerceDefault()` (WooCommerce body class / CSS/JS signals).

### 6.4 Product extraction

| Extractor | Strategy |
|---|---|
| `ProductDetailExtractors.jsoupDefault()` | JSON-LD → OG → HTML selectors → title fallback |
| `ProductDetailExtractors.woocommerceDefault()` | WooCommerce CSS selectors → JSON-LD fallback |
| `ProductCardExtractors.jsoupDefault()` | Broad CSS patterns for product grids |
| `ProductCardExtractors.woocommerceDefault()` | WooCommerce `li.product` selectors |
| `JsonLdProductExtractor` | Jackson JSON-LD parser; handles `@graph`, `@type` array |

### 6.5 Document ingestion bridge

`Ingestors.simple()` → `SimpleDocumentIngestionService` → sequential loop calling `indexer.index(mapper.map(item))`.
`Mappers.webPage()` maps `WebPage` → `Document`: URL as id, title+bodyText as rawContent, title and body as structured fields. Web metadata (HTML, headers, statusCode) stuffed into `DocumentMetadata.attributes`.

### 6.6 SiteExporter

Complete pipeline: crawl → mirror HTML to disk → (PDF only) download assets + rewrite links → publish via format driver. Three format drivers: PDF (`openhtmltopdf-pdfbox`), Markdown (Jsoup extraction), EPUB (hand-rolled ZIP, EPUB 3). The exporter has no connection to the core IR engine.

---

## 7. Test & Verification Evidence

### 7.1 Test counts and module coverage

| Module | Tests | Failures |
|---|---|---|
| codex-ir-core | 197 | 0 |
| codex-ir-web | 466 | 0 |
| codex-ir-app | 359 | 0 |
| **Total** | **1,022** | **0** |

### 7.2 Core IR tests mapped to capabilities

| Test class | Tests | What it verifies |
|---|---|---|
| `RankersTest` | 12 | BinaryRanker returns 1.0; TF-IDF IDF formula value; BM25 length normalization direction (short > long), null-length edge case |
| `FieldAwareRankingTest` | 10 | Field boost formula (1e-12 tolerance); neutral context invariance; title-match outranks body-match in full search; raw-content docs unaffected; unknown-field default weight=1.0; TF-IDF and BM25 both tested |
| `FieldAwarePostingsTest` | 10 | `Posting.fieldFrequencies` populated; single/multi-field; same term in two fields; isolation between documents; snapshot propagation; batch pipeline propagation |
| `FieldAwareIndexingTest` | 6 | Field aggregation contract (body-only, title+body, all-blank fallback, blank-field skip); explicit comment: per-field search intentionally not tested |
| `FieldAwareVectorIndexingTest` | 7 | Full lexical+vector pipeline; fields-take-precedence-over-rawContent; stop-word pipeline; `vectorIndexingMustUsePreprocessedDocumentNotOriginal` (critical: verifies rawContent terms not indexed when fields present) |
| `BatchIndexerTest` | 7 | Batch lexical and vector; IDF consistency across full corpus before vectorization; empty batch; single-doc fallback |
| `SnapshotSearchTest` | 4 | Snapshot frozen after subsequent writes; fresh snapshot sees new docs; read isolation contract |
| `SearchersTest` | 3 | Factory wiring; null query → empty; blank query → empty |
| `VectorSearcherTest` | 4 | Rare-term ranks first; term-specific doc ranks first; null/blank query → empty |
| `DebouncerTest` | 9 | Debounce, key replacement, independent keys, zero-delay, cancellation — with `CountDownLatch`/`AtomicInteger` |
| `CorpusStatisticsTest` | 20 | Empty corpus, avgLength, zero-division protection, version increment, document replacement, `from()` factory |
| `DocumentPreprocessorTest` | 8 | Preprocessing pipeline (titles, tokens, field analysis) |
| `PreprocessedDocumentTest` | 11 | Token artifact structure and contracts |
| `FieldAnalyzedDocumentTest` | 12 | Field-level token sequence contracts |
| `NormalizersTest` | 33 | All normalizer implementations + composition |
| `TokenizersTest` | 20 | Whitespace tokenizer edge cases |
| `WeightersTest` | 4 | TF and TF-IDF weighter outputs |
| `DocumentWeighterTest` | 5 | TF weighter with cached vs. recomputed term frequencies |
| `VocabulariesTest` | 11 | Vocabulary term ID assignment, bidirectional lookup |
| `SparseCosineSimilarityTest` | 1 | Score between 0 and 1; vocabulary size side effect |

### 7.3 Web tests mapped to capabilities

All web tests use inline HTML strings or stub `WebHttpFetcher` lambdas. No test makes a real HTTP request or spins up an embedded server.

| Test class | Tests | What it verifies |
|---|---|---|
| `PageClassifiersTest` / `PageClassifiersJsoupDefaultTest` | 44 | HTML heuristics for product/category/article detection |
| `UrlClassifiersTest` | 35 | URL pattern classification across 14 types |
| `UrlFiltersTest` | 11 | Composable URL predicate logic |
| `ProductDetailExtractorsTest` / `JsoupDefaultTest` | 46 | JSON-LD and HTML product detail extraction |
| `ProductCardExtractorsTest` / `JsoupDefaultTest` | 32 | Product grid card extraction |
| `ProductDiscoverersTest` | 9 | End-to-end discovery routing by page type |
| `ProductDiscoveryCollectorsTest` | 8 | Collector pipeline |
| `ExtractionResultModelsTest` | 86 | Record model construction and contracts |
| `ProductPriceParserTest` | 30 | Price string parsing, currency extraction |
| `CardClassifierTest` | 18 | Card classification heuristics |
| `CanonicalProductKeyTest` | 15 | Product deduplication key logic |
| `SitemapParserTest` | 13 | Sitemap XML parsing (inline XML) |
| `RobotsParserTest` | 13 | Robots.txt parsing (stub fetcher) |
| `PageMetadataExtractorsTest` / `PageMetadataTest` | 26 | Metadata extraction and record contracts |
| `UriCanonicalizersTest` | 12 | URI normalization pipeline |
| `HtmlTextDecoderTest` | 14 | HTML text cleaning |
| `JsonLdBlockExtractorTest` | 5 | JSON-LD script block extraction |
| `HeadingExtractorTest` | 7 | h1/h2 heading extraction |
| `MetaTagExtractorTest` | 8 | `<title>`, `<meta name=description>`, canonical link |
| `OpenGraphExtractorTest` | 4 | OG property extraction |
| `TwitterCardExtractorTest` | 3 | Twitter card meta extraction |
| `RobotsMetaExtractorTest` | 4 | `<meta name=robots>` directive extraction |
| `DiscoveryReportWritersTest` | 8 | JSON report writing |
| `ProductDiscoveryQualityFormatterTest` | 15 | Quality report formatting |

### 7.4 App tests mapped to capabilities

| Test class | Tests | What it verifies |
|---|---|---|
| `SiteExporterCommandTest` | 16 | CLI flag parsing, defaults, validation, from-mirror mode |
| `EpubPublicationDriverTest` | 23 | EPUB pipeline; structure only |
| `ReaderHtmlWriterTest` | 13 | XHTML chapter writing |
| `MarkdownPublicationDriverTest` | 10 | Markdown output generation |
| `HtmlLinkRewriterTest` | 18 | Local link rewriting |
| `LocalPathResolverTest` | 15 | Asset path resolution |
| `SiteAssetServiceTest` | 12 | Asset download (one expected connection-refused error in output — test exercises offline path) |
| `DiscoveredOrderPublicationOrderingStrategyTest` | 6 | Publication ordering |
| `SitemapUrlExtractorTest` | 8 | Sitemap URL extraction in app layer |

### 7.5 Verification gaps

- **BM25 parameter sensitivity** (k1/b): not tested. Formula value correctness not asserted to a reference calculation.
- **Cosine similarity mathematical correctness**: not verified against a reference calculation (only range and ordering are checked).
- **Concurrent write/read on InvertedIndex and Corpus**: no concurrency test exists.
- **No real HTTP integration test**: web tests use stubs/inline HTML only.
- **Full crawl → index → search pipeline**: not tested end-to-end (only sub-pipelines are tested).
- **Playwright fetcher**: stub, no test.
- **BM25 with null document length** is tested (returns 0.0), but the implication (document must have length metadata set by preprocessing) is not verified as an invariant of the full pipeline.

---

## 8. Build / Runtime State

### 8.1 Build environment

| Property | Value |
|---|---|
| JDK | OpenJDK 25.0.2 (Eclipse Temurin), required — no other version works |
| Maven | 3.9.15 (no wrapper — install externally) |
| Surefire | 3.2.5 |

### 8.2 Build result (run 2026-09-04)

```
mvn compile  → BUILD SUCCESS (all 3 modules)
mvn test     → BUILD SUCCESS
  codex-ir-core:  197 tests,   0 failures, 0 errors, 0 skipped
  codex-ir-web:   466 tests,   0 failures, 0 errors, 0 skipped
  codex-ir-app:   359 tests,   0 failures, 0 errors, 0 skipped
  TOTAL:        1,022 tests,   0 failures
```

One non-fatal warning in `codex-ir-app` compile: `openhtmltopdf-pdfbox-1.0.10.jar` is a filename-based automodule — Maven warns not to publish this project to a public artifact repository.

One expected log line during app tests: `[Assets][ERROR] Failed to fetch https://example.com/img.png: connection refused` — this is intentional test behavior in `SiteAssetServiceTest` (offline asset fetch path), not a test failure.

### 8.3 Key dependency versions

| Dependency | Version | Note |
|---|---|---|
| `slf4j-api` | 2.0.12 | Current |
| `logback-classic` | 1.5.6 | Current |
| `junit-jupiter` | 5.10.2 | Slightly behind 5.11.x, functional |
| `jsoup` | 1.17.2 | Slightly behind 1.18.x, functional |
| `playwright` | 1.44.0 | Mid-2024; 1.47+ exists; not critical |
| `jackson-databind` | 2.17.2 | Current 2.17.x stable |
| `openhtmltopdf-pdfbox` | 1.0.10 | **Maintenance risk**: library is archived upstream; last release 2022. No security patches. Functional but not maintained. |

### 8.4 Prerequisites

- `npx playwright install` — required once before any test touching `WebPageFetcher` (Playwright binary). Not needed for current tests (all use stubs).
- No CI configuration exists. No Makefile. No pre-commit hooks.

### 8.5 Runnable demos

| Entry point | What it does |
|---|---|
| `codex.scraper.Main` | Runs two real HTTP crawls of `demo.dotcms.com` — lexical BM25 search + vector cosine search |
| `codex.scraper.DiscoveryRunner` | Sitemap-based product extraction; configurable via `--sitemap <url>` |
| `codex.scraper.QuickDiscoveryRunner` | Hard-coded to `syjleathers.com` product sitemaps; dev convenience only |
| `codex.apps.siteexporter.SiteExporterCommand` | Full crawl-to-PDF/Markdown/EPUB pipeline |

All demos require live network access except `--from-mirror` mode of the site exporter.

---

## 9. Current Product Use Cases

### 9.1 Supported today

1. **Lexical document search over an in-memory corpus**: index a document collection, query with BM25/TF-IDF/binary ranking, retrieve ranked results. Field-weight boosting available via `RankingContext` and `FieldWeights`.

2. **Sparse vector document search**: index a collection with TF or TF-IDF term weights, query with cosine similarity. Linear scan. Threshold-filtered results.

3. **Structured-field document indexing and boosted retrieval**: supply `Document.fields(Map<String,String>)`, configure `FieldWeights`, retrieve ranked results where title matches outrank body matches.

4. **Batch indexing with globally consistent IDF**: index a full corpus in batch mode (`Indexers.batchLexicalAndVector()`), produce document vectors using IDF computed across the full corpus rather than a partial index.

5. **Snapshot-based search isolation**: take a corpus/index snapshot, perform reads against it while new documents are ingested. Read-write boundary is enforced.

6. **HTTP web crawling with product extraction**: crawl a site via BFS or sitemap traversal, classify pages, extract product details and product cards, write a JSON report. Functional with WooCommerce/WordPress-centric defaults.

7. **Site-to-document export (PDF, Markdown, EPUB)**: crawl a site, mirror HTML to disk, publish to a chosen output format.

### 9.2 Suggested by architecture or docs but not yet supported end-to-end

- **Phrase/proximity search**: positions are stored in postings but no phrase query evaluator exists.
- **Boolean retrieval (AND/NOT)**: only union semantics are implemented in `SimpleSearcher`.
- **BM25 parameter tuning**: the constructor exists but is not accessible via the public factory.
- **Field-aware vector ranking (BM25F-style)**: `VectorIndexer` is field-unaware.
- **Score explanation/trace**: roadmap item IR-5; no code.
- **Retrieval quality evaluation (precision@k, etc.)**: roadmap item IR-6; no code.
- **Dense/semantic vector retrieval**: listed as future; no code.
- **JavaScript-rendered page crawling**: Playwright stub returns empty; no real implementation.
- **IR-indexed site search** (crawl a site, index with IR engine, search): conceptually enabled by `Mappers.webPage()` and `Ingestors.simple()`, but not wired in any entry point. `Main` does this (crawl → index → search), but it is a demo, not a configured application.

---

## 10. Documentation / Knowledge Landscape

### 10.1 Sources of truth inventory

| Source | Location | Status |
|---|---|---|
| CKF knowledge bundle | `docs/knowledge/` | Current and well-maintained for IR-0 through IR-4. `index.md` is navigable. |
| CKF engineering logs (core) | `docs/knowledge/logs/core/` | Complete for IR-0 through IR-4. |
| CKF engineering logs (site exporter) | `docs/knowledge/logs/site-exporter/` | 17 entries through Phase 11 (EPUB). |
| CKF engineering logs (web extraction) | none | **Absent.** 38 task files exist under `docs/tasks/apps/` but no CKF log covers web/extraction work. |
| OSK engineering index | `docs/engineering/ENGINEERING_LOG.md` | Scaffolding only; 1 real entry (interview notes), 1 template row. |
| OSK engineering agents | `docs/engineering/agents/` | Contains interview notes and README stubs. |
| CLAUDE.md | `/CLAUDE.md` | Updated 2026-09-04. Entry point class names now match actual files. |
| AGENTS.md | `/AGENTS.md` | Consistent with CLAUDE.md. |
| Historical ADRs | `docs/adrs/` | ADR-001 through ADR-005. ADR-004 "not yet supported" table stale (field boosting is now implemented). ADR-005 CKF status `proposed` is stale (IR-3 and IR-4 are complete). |
| New ADR location | `docs/adr/` | README stub only; no ADRs filed here yet. |
| Roadmap | `docs/roadmap/ROADMAP.md` | Placeholder table only — no content. |
| Active task plan | `docs/tasks/core/Plan01-Roadmap-Field-AwareIR.md` | The actual next-item list lives here, not in `docs/roadmap/`. |
| Future-Forward.md | `docs/Future-Forward.md` | **Significantly stale.** Describes field-aware documents, field weighting, and web crawling as future work; all three are now implemented. |
| CODING_IDENTITY.md | `docs/CODING_IDENTITY.md` | 12 design principles; appears current and consistent with implementation. |
| README (root) | `README.md` | Comprehensive. Documents field aggregation behavior, snapshot boundary, site-exporter CLI, known limitations. Current. |
| Module READMEs | Each module root | Exist; not assessed in depth. |
| package-info.java files | All packages | Complete coverage across all modules; updated through IR-4. |

### 10.2 Duplication and conflicts

- **CLAUDE.md vs. AGENTS.md** entry point names: resolved as of 2026-09-04. Previously, CLAUDE.md listed `codex.Main` while AGENTS.md listed `codex.scraper.Main` (the correct name).
- **ADR-004** "not yet supported" table: field weight/boost is marked not implemented, but IR-4 delivers this. ADR not updated.
- **ADR-005 CKF status** `proposed`: IR-3 and IR-4 completed the proposed incremental slices. Status not updated.
- **`docs/knowledge/phases/core/ir-2-field-provenance.md`** exists as a phase spec AND as a log entry under `docs/knowledge/logs/`. The log entry appears to be a newer, untracked file. Two files with slightly different content for the same phase.
- **`docs/roadmap/ROADMAP.md`** is a placeholder. `docs/tasks/core/Plan01-Roadmap-Field-AwareIR.md` is the functional roadmap. These should be reconciled or linked.
- **`docs/reports/core/ENGINEERING_LOG.md`** links IR-0 through IR-2 CKF logs but not IR-3 or IR-4. Two phases behind.

### 10.3 Stale documentation

| Document | What is stale |
|---|---|
| `docs/Future-Forward.md` | Describes field-aware docs, field weighting, web crawling as future — all implemented |
| `docs/adrs/ADR-004.md` | "Not yet supported" field weighting is now implemented (IR-4) |
| `docs/adrs/ADR-005.md` | CKF status `proposed`; IR-3 + IR-4 delivered incremental slices |
| `docs/reports/core/ENGINEERING_LOG.md` | Missing IR-3 and IR-4 link entries |
| `docs/roadmap/ROADMAP.md` | Empty; does not reflect actual direction |

### 10.4 Orphaned / unlinked content

- `docs/engineering/agents/` contains READMEs and the interview notes report, but `docs/engineering/ENGINEERING_LOG.md` only links the interview notes. This report will be the second linked entry.
- `docs/tasks/apps/` contains 38 task files for information extraction work with no corresponding CKF engineering log or knowledge entries.

---

## 11. Roadmap vs. Implementation

Primary roadmap source: `docs/tasks/core/Plan01-Roadmap-Field-AwareIR.md`.
Secondary: roadmap mentions in CKF logs.

| Roadmap Item | Classification | Evidence |
|---|---|---|
| IR-0: Read/Write Boundary | **Evidently complete** | CKF log; `SnapshotSearchTest` (4 tests); `IndexSnapshot`, `CorpusSnapshot` in code |
| IR-0.5: Batch Index Build | **Evidently complete** | CKF log; `BatchIndexerTest` (7 tests); `Indexers.batchLexicalAndVector` exists |
| IR-1: PreprocessedDocument | **Evidently complete** | CKF log; `PreprocessedDocumentTest` (11 tests); type in production |
| IR-2: Field Provenance (FieldAnalyzedDocument) | **Evidently complete** | CKF log; `FieldAnalyzedDocumentTest` (12 tests); type in production |
| IR-3: Field-Aware Postings | **Evidently complete** | CKF log; `FieldAwarePostingsTest` (10 tests); `Posting.fieldFrequencies`, `InvertedIndex.addFieldOccurrence` in production |
| IR-4: Field-Aware Ranking / Boosting | **Evidently complete** | CKF log; `FieldAwareRankingTest` (10 tests); `FieldWeights`, `RankingContext`, `Ranker.score(term,posting,ctx)` in production |
| IR-5: Score Explanation | **Not evident** | Named in IR-4 CKF log as next step; no `ScoreExplanation` type; no CKF log |
| IR-6: Evaluation Harness | **Not evident** | Listed in plan; no fixture queries, no precision@k metrics, no test harness; `docs/engineering/agents/` scaffolding exists but empty |
| IR-7 through IR-13 (n-grams, dense vectors, hybrid, etc.) | **Not evident** | Listed as future in `Plan01`; no code |
| Site Exporter Phases 3–11 | **Evidently complete** | 17 CKF logs with `ckf_status: completed`; PDF/Markdown/EPUB drivers; 29+ test classes |
| Web crawling / product extraction | **Implemented, undocumented** | Substantial code and 466 tests; no CKF logs; no knowledge entries |

---

## 12. Risks, Gaps, and Open Questions

### Implementation risks

1. **`JdkWebHttpFetcher.close()` is broken** — inverted null-check means it never closes the `HttpClient`. The virtual-thread executor is also never shut down (`// todo`). In long-running crawler sessions, this is a resource leak.

2. **`TfIdfDocumentWeighter` takes a live `InvertedIndex`** (not a snapshot) — DF values could diverge from the snapshot-based `TfIdfRanker` under concurrent indexing. In practice, sequential indexing is unaffected, but this is a latent consistency risk.

3. **`VectorSearcher` is O(N) linear scan** — adequate for a few thousand documents but will not scale. No ANN index structure exists or is planned.

4. **`Bm25Ranker` k1/b not publicly tunable** — the class has a two-parameter constructor, but `Rankers.bm25(corpus, index)` always uses hardcoded defaults (k1=1.2, b=0.75). Callers cannot tune without reflection.

5. **`openhtmltopdf-pdfbox` is abandoned upstream** — library is archived on GitHub, last release 2022. No security patches. The site-exporter depends on it for PDF rendering.

6. **Playwright stub** — `dynamicHtml()` always returns `Optional.empty()`. No mechanism exists to crawl JavaScript-heavy pages. This is undocumented in the CLI/README.

7. **Positions stored but never consumed** — full positional indexing is happening (position lists stored in every posting), adding memory overhead with no current payoff.

### Architectural gaps

8. **No connection between IR engine and site exporter** — the site exporter crawls, mirrors, and publishes sites but does not index them or support search. The pipeline is export-only.

9. **Field-aware ranking is lexical-only** — vector scores are computed from the whole-document representation. There is no BM25F or field-weighted cosine path. Field boosting only works with `SimpleSearcher`.

10. **WooCommerce-centrism in the "default" web classifier** — the exported "default web" URL classifier and default sitemap paths embed WordPress/WooCommerce assumptions. This is appropriate for the current use case but is a maintenance concern if myIR is positioned as a general-purpose IR platform.

11. **No evaluation capability** — without an evaluation harness (IR-6), there is no way to measure whether ranking changes (e.g., changing k1/b, adding a new normalizer, enabling phrase boost) improve or harm retrieval quality.

12. **Web extraction has no CKF documentation trail** — 38 task files exist under `docs/tasks/apps/` but no corresponding knowledge entries, decisions, or engineering logs exist for the web/extraction module.

---

## 13. Evidence / Relevant Paths

| Area | Key path |
|---|---|
| IR engine root | `codex-ir-core/src/main/java/codex/ir/` |
| Indexing pipeline | `codex-ir-core/src/main/java/codex/ir/indexer/Indexers.java` |
| InvertedIndex | `codex-ir-core/src/main/java/codex/ir/indexer/InvertedIndex.java` |
| InvertedIndex impl | `codex-ir-core/src/main/java/codex/ir/indexer/InvertedIndexes.java` |
| Field-aware postings | `codex-ir-core/src/main/java/codex/ir/indexer/FieldTokenSequence.java` |
| Ranking models | `codex-ir-core/src/main/java/codex/ir/ranking/Rankers.java` |
| Field weights | `codex-ir-core/src/main/java/codex/ir/ranking/FieldWeights.java` |
| Ranking context | `codex-ir-core/src/main/java/codex/ir/ranking/RankingContext.java` |
| Ranker interface (boost default) | `codex-ir-core/src/main/java/codex/ir/ranking/Ranker.java` |
| Searchers | `codex-ir-core/src/main/java/codex/ir/search/Searchers.java` |
| SimpleSearcher | `codex-ir-core/src/main/java/codex/ir/search/SimpleSearcher.java` |
| VectorSearcher | `codex-ir-core/src/main/java/codex/ir/search/VectorSearcher.java` |
| Corpus | `codex-ir-core/src/main/java/codex/ir/corpus/Corpora.java` |
| Normalizers | `codex-ir-core/src/main/java/codex/ir/normalizer/Normalizers.java` |
| Vector store | `codex-ir-core/src/main/java/codex/ir/vector/store/VectorStores.java` |
| HTTP fetcher bug | `codex-ir-web/src/main/java/codex/ir/ingestion/crawler/fetcher/WebHttpFetchers.java` |
| BFS traversal | `codex-ir-web/src/main/java/codex/ir/ingestion/crawler/internal/traversal/SiteTraversalStrategy.java` |
| Sitemap traversal | `codex-ir-web/src/main/java/codex/ir/ingestion/crawler/internal/sitemap/SitemapSiteTraversalStrategy.java` |
| URL classifier | `codex-ir-web/src/main/java/codex/ir/ingestion/crawler/classifier/UrlClassifiers.java` |
| Product discovery | `codex-ir-web/src/main/java/codex/ir/ingestion/crawler/product/ProductDiscoverers.java` |
| Ingestion bridge | `codex-ir-web/src/main/java/codex/ir/ingestion/Ingestors.java` |
| Web → IR mapper | `codex-ir-web/src/main/java/codex/ir/ingestion/Mappers.java` |
| App main | `codex-ir-app/src/main/java/codex/scraper/Main.java` |
| Site exporter | `codex-ir-app/src/main/java/codex/apps/siteexporter/SiteExporterCommand.java` |
| CKF index | `docs/knowledge/index.md` |
| CKF logs — core | `docs/knowledge/logs/core/` |
| CKF logs — site exporter | `docs/knowledge/logs/site-exporter/` |
| Roadmap plan | `docs/tasks/core/Plan01-Roadmap-Field-AwareIR.md` |
| Stale future doc | `docs/Future-Forward.md` |
| Historical ADRs | `docs/adrs/ADR-001.md` through `ADR-005.md` |

---

## 14. Recommended Questions for the Next Planning Session

These are open questions and decision points, not proposals.

1. **IR-5 (Score Explanation) vs. IR-6 (Evaluation Harness) — which first?** Score explanation helps debugging individual queries; an evaluation harness helps measure ranking quality improvements across all queries. Which is more useful given the current usage context?

2. **Should field-aware boosting be extended to the vector path?** BM25F-style or field-weighted cosine would require `VectorIndexer` to consume `FieldAnalyzedDocument`. Is this a priority, or is lexical field boosting sufficient for the near term?

3. **When should `VectorSearcher`'s O(N) linear scan be replaced?** At what document collection size does this become a real constraint? Is there a near-term use case that would hit this limit?

4. **Should the site exporter connect to the IR engine?** The exporter currently produces a static artifact. Is search-over-the-exported-site a desired capability? This would require integrating the two pipelines.

5. **What is the intended scope of the "web" module?** Should WooCommerce/WordPress-specific logic remain in the reusable exported classifier, or should it be moved to the application layer? This decision affects API surface.

6. **Should the `openhtmltopdf-pdfbox` dependency be replaced?** The library is archived. Is PDF export still a required output format? If so, a migration to a maintained library (e.g., Flying Saucer fork, OpenPDF) should be planned.

7. **Should `Rankers.bm25` expose k1/b parameters?** The factory API currently hardcodes defaults. Is parameter tuning a desired capability at the API level?

8. **What should be done with positional postings?** Phrase search, passage retrieval, and KWIC snippets all use positions. Should the effort of building positional data be paid off, or should positions be made optional to save memory?

9. **Should the web extraction subsystem receive CKF documentation?** 38 task files document the work but no CKF engineering logs or knowledge entries exist for this subsystem. Should this gap be filled before further web work begins?

10. **What is the evaluation criterion for ranking quality?** Without a benchmark dataset or test queries with known relevant documents, there is no way to know if the current BM25 + field-boost configuration performs well. Should acquiring or constructing a small evaluation set be a near-term task?

---

*Report produced 2026-09-04. Primary evidence source: repository code, test suite, and build output. Documentation consulted as secondary evidence only.*
