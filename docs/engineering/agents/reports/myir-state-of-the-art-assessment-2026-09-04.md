---
type: Engineering Report
title: "myIR State of the Art Assessment — September 2026"
description: "Evidence-based assessment of myIR produced from a fresh audit of source, tests, build output, and documentation. Distinguishes implemented, planned, proposed, and historical artifacts and recommends next steps."
tags: [assessment, state-of-the-art, core, web, app, roadmap, documentation]
timestamp: 2026-09-04T00:00:00Z
ckf_version: "0.1"
ckf_status: completed
ckf_scope: cross-cutting
ckf_owner: project
---

# myIR State of the Art Assessment — September 2026

This report is the answer to the brief: *"produce an evidence-based assessment of the myIR repository as if you had just joined the engineering team."* Every conclusion below cites a file in the working tree inspected on 2026-09-04.

---

## 1. Executive Summary

myIR is a Java 25, in-memory Information Retrieval (IR) laboratory. It combines a classical lexical engine, a sparse-term vector retriever, a web acquisition layer, and a site-to-publication exporter into a single multi-module Maven project with strict JPMS boundaries. It is intentionally educational and self-contained; it does not attempt to replace Lucene or Elasticsearch ([README.md](../../../../README.md) §1).

The IR pipeline (tokenize → normalize → index → rank → search) is implemented, field-aware at the lexical layer, and covered by 1,022 passing tests split across three modules:

| Module | Tests | Role |
|---|---:|---|
| `codex-ir-core` | 197 | Tokenization, normalization, indexing, ranking, search, sparse vectors, field-aware lexical boosting ([RankersTest](../../../../codex-ir-core/src/test/java/codex/ir/ranking/RankersTest.java), [FieldAwareRankingTest](../../../../codex-ir-core/src/test/java/codex/ir/ranking/FieldAwareRankingTest.java)) |
| `codex-ir-web` | 466 | HTTP/BFS/sitemap crawling, page and URL classification, product extraction, ingestion bridge ([PageClassifiersTest](../../../../codex-ir-web/src/test/java/codex/ir/ingestion/crawler/classifier/PageClassifiersTest.java), [ProductDetailExtractorsTest](../../../../codex-ir-web/src/test/java/codex/ir/ingestion/crawler/product/ProductDetailExtractorsTest.java)) |
| `codex-ir-app` | 359 | Site exporter to PDF/Markdown/EPUB and discovery runners ([SiteExporterCommandTest](../../../../codex-ir-app/src/test/java/codex/apps/siteexporter/SiteExporterCommandTest.java)) |

The most recent core deliveries (IR-0 through IR-4) are complete. The committed roadmap is empty by design: the only two candidates, IR-5 Score Explanation and IR-6 Evaluation Harness, are explicitly *unselected* and require a human planning session to choose one ([ROADMAP.md](../../../../docs/roadmap/ROADMAP.md)). The site-exporter pipeline has its own end-to-end completion trail through Phase 11 (EPUB) and is independently feature-complete for its current scope.

Three things drive what the team should care about next:

1. **No relevance evaluation harness exists** — every test is a mechanical or fixture-level correctness test. There is no qrels, precision/recall, MRR, nDCG, or A/B loop. The system cannot say whether a ranking change is an improvement ([myir-interview-deep-dive-notes.md](../../../../docs/engineering/agents/reports/myir-interview-deep-dive-notes.md) §2, [knowledge-reconciliation-2026-09.md](../../../../docs/engineering/agents/reviews/knowledge-reconciliation-2026-09.md) §"QA evidence assessment").
2. **Several latent defects in the web/app layer are documented but unfixed** — the JdkWebHttpFetcher `close()` has an inverted null-check ([WebHttpFetchers.java:111-117](../../../../codex-ir-web/src/main/java/codex/ir/ingestion/crawler/fetcher/WebHttpFetchers.java)), the BM25 k1/b parameters are not reachable through the public factory ([Rankers.java:81-83](../../../../codex-ir-core/src/main/java/codex/ir/ranking/Rankers.java)), and Playwright is a no-op stub ([README.md](../../../../README.md) §"Web Ingestion").
3. **The documentation landscape is partially stale** — `Future-Forward.md` still describes field-aware documents, field weighting, and web crawling as future work, but all three are implemented ([Future-Forward.md](../../../../docs/Future-Forward.md) vs. [ir-4-field-aware-ranking.md](../../../../docs/knowledge/logs/core/ir-4-field-aware-ranking.md)).

The system is a defensible, well-bounded experimental platform. It is **not** a production search engine: storage is in memory, scaling is bounded, the dynamic-renderer and the persisted-corpus paths are placeholders, and no relevance measurement exists.

---

## 2. Current Architecture

### 2.1 Module / JPMS map

Three Maven modules, each a named JPMS module, with one-way dependency direction `app → web → core` ([AGENTS.md](../../../../AGENTS.md) §"Module layout", [CLAUDE.md](../../../../CLAUDE.md) §"JPMS module graph"):

```text
codex-ir-core  ←  codex-ir-web  ←  codex-ir-app
```

| Maven module | JPMS module | Public surface | Internal / non-exported |
|---|---|---|---|
| `codex-ir-core` | `codex.ir.core` | All packages except `codex.ir.util` ([codex-ir-core/src/main/java/module-info.java](../../../../codex-ir-core/src/main/java/module-info.java)) | `codex.ir.util` (TermWeightingUtils) |
| `codex-ir-web` | `codex.ir.web` | 7 packages: `ingestion`, `ingestion.crawler`, `ingestion.crawler.classifier`, `ingestion.crawler.filter`, `ingestion.crawler.metadata`, `ingestion.crawler.product`, `canonicalizer` ([codex-ir-web/src/main/java/module-info.java](../../../../codex-ir-web/src/main/java/module-info.java)) | `ingestion.crawler.fetcher`, `ingestion.crawler.internal.*`, `web.util` |
| `codex-ir-app` | `codex.ir.app` | No exports (entry-point only) | `codex.scraper`, `codex.apps.siteexporter` |

Direction and packaging are holding. The site exporter, the scraper demos, and the discovery runner are application code; the IR core has no awareness of HTML, HTTP, or WordPress.

### 2.2 Architectural fingerprint

- **Interface + Factory.** Every domain concept is an interface paired with a static factory (`Xxx` + `Xxxes`); the implementation is a package-private inner class of the factory. Confirmed list spans 13+ core pairs and 13+ web pairs ([CLAUDE.md](../../../../CLAUDE.md) §"Interface + Factory pattern").
- **Records over maps.** Domain data is `Document`, `Posting`, `SearchResult`, `SparseDocumentVector`, `WebPage`, `PageMetadata`, etc. — all records ([Document.java](../../../../codex-ir-core/src/main/java/codex/ir/Document.java), [Posting.java](../../../../codex-ir-core/src/main/java/codex/ir/indexer/Posting.java), [WebPage.java](../../../../codex-ir-web/src/main/java/codex/ir/ingestion/WebPage.java)).
- **Snapshot read boundary.** Writes go to mutable `Corpus` and `InvertedIndex`; reads go through `CorpusSnapshot` and `IndexSnapshot` taken at publication time ([README.md](../../../../README.md) §"Snapshot Read Boundary", [ir-0-read-write-boundary.md](../../../../docs/knowledge/logs/core/ir-0-read-write-boundary.md)).
- **In-memory by design.** All core stores (corpus, index, vector store, vocabulary) are in-memory. Explicitly not introducing persistence without an ADR ([ADR-003.md](../../../../docs/adrs/ADR-003.md)).
- **Virtual threads for blocking I/O.** BFS site traversal uses a `LinkedBlockingQueue` frontier with `VTExecutor` ([AGENTS.md](../../../../AGENTS.md) §"Concurrency"). Java 25 (`source=25 target=25`) is pinned in `pom.xml` ([AGENTS.md](../../../../AGENTS.md) §"Build & Test").
- **No hidden magic.** No reflection, dynamic proxies, bytecode manipulation, or annotation-driven behavior. `Jpms` is the only "magic," and it is explicit ([CLAUDE.md](../../../../CLAUDE.md) §"No hidden magic").
- **Documentation discipline.** OSK classifies artifacts by purpose, not by task. Knowledge lives in `docs/knowledge/`, engineering evidence in `docs/engineering/`, decisions in `docs/adr/` and `docs/adrs/`, and roadmap in `docs/roadmap/` ([OSK.md](../../../../docs/OSK.md) §"Information Model").

### 2.3 IR pipeline (implemented)

The end-to-end lexical path is wired and tested:

```text
Document (id, rawContent | fields, metadata)
   ↓ DocumentPreprocessor
   │   resolveContent() → field-aggregate or rawContent
   │   tokenize → normalize → token list
   │   analyzeFields() → per-field FieldTokenSequence
   ↓ FieldAnalyzedDocument
   ├─ LexicalIndexer  → Corpus.add + InvertedIndex.add(term, docId, position)
   │                  → InvertedIndex.addFieldOccurrence(term, docId, field)
   └─ VectorIndexer   → DocumentWeighter → Vectorizer → DocumentVectorStore
Search
   ↓ SimpleSearcher.searchDetailed(query)
   │   tokenize → normalize
   │   InvertedIndex.getPostings(term) ∪ across terms
   │   Ranker.score(term, posting, RankingContext)
   │   sort desc → List<SearchResult>
```

Sources: [Indexers.java](../../../../codex-ir-core/src/main/java/codex/ir/indexer/Indexers.java), [FieldAwarePostingsTest.java](../../../../codex-ir-core/src/test/java/codex/ir/indexer/FieldAwarePostingsTest.java), [FieldAwareRankingTest.java](../../../../codex-ir-core/src/test/java/codex/ir/ranking/FieldAwareRankingTest.java), [Rankers.java](../../../../codex-ir-core/src/main/java/codex/ir/ranking/Rankers.java), [VectorSearcher.java](../../../../codex-ir-core/src/main/java/codex/ir/search/VectorSearcher.java).

### 2.4 Web ingestion pipeline (implemented)

```text
Seed URI(s)
   ↓ UriCanonicalizer → WebPageSourceStrategy
   ├─ JsoupWebPageFetcher (default static HTML)
   └─ PlaywrightWebPageFetcher (stub; returns empty)
   ↓ WebPage → PageMetadataExtractor → PageClassifier
            → ProductDiscoverer (product/cards)
            → DocumentMapper → Document
            → Ingestors.simple() → Indexer.index(...)
```

The site exporter is a separate downstream pipeline: `crawl → mirror HTML → assets (PDF only) → PublicationDriver → PDF | Markdown | EPUB`. It does **not** index or search; it is explicitly independent of the IR engine ([current-system.md](../../../../docs/knowledge/architecture/current-system.md)).

### 2.5 Intentional application specialization

- The default URL/page classifier (`UrlClassifiers.wordpressWooCommerceDefaultWeb()`) embeds WordPress/WooCommerce-specific slug rules. This is recorded as an *intentional application specialization*, not a core defect ([extraction-and-applications.md](../../../../docs/knowledge/web/extraction-and-applications.md), [knowledge-reconciliation-2026-09.md](../../../../docs/engineering/agents/reviews/knowledge-reconciliation-2026-09.md) §"QA evidence assessment").
- `QuickDiscoveryRunner` hard-codes `syjleathers.com` URLs — a developer convenience in the app layer, acceptable but not a generic entry point ([CLAUDE.md](../../../../CLAUDE.md) §"codex-ir-app packages").

---

## 3. Capability Inventory

This inventory distinguishes **implemented and wired end-to-end** from **implemented but partial** from **planned/proposed** and from **historical/intentionally not implemented**. Each row cites evidence.

### 3.1 Core IR — implemented and tested

| Capability | Evidence |
|---|---|
| Whitespace tokenization | [TokenizersTest.java](../../../../codex-ir-core/src/test/java/codex/ir/tokenizer/TokenizersTest.java) (20 tests) |
| Composable normalization (lowercase, accent fold, punctuation trim, stop-words, chains) | [NormalizersTest.java](../../../../codex-ir-core/src/test/java/codex/ir/normalizer/NormalizersTest.java) (33 tests); [Normalizers.java](../../../../codex-ir-core/src/main/java/codex/ir/normalizer/Normalizers.java) |
| English + Spanish stop-word lists | [stopwords_en.txt](../../../../codex-ir-core/src/main/resources/stopwords_en.txt), [stopwords_es.txt](../../../../codex-ir-core/src/main/resources/stopwords_es.txt) |
| In-memory positional inverted index | [InvertedIndex.java](../../../../codex-ir-core/src/main/java/codex/ir/indexer/InvertedIndex.java), [InMemoryInvertedIndex](../../../../codex-ir-core/src/main/java/codex/ir/indexer/InvertedIndexes.java) |
| Corpus + corpus statistics, eager or debounced | [Corpora.java](../../../../codex-ir-core/src/main/java/codex/ir/corpus/Corpora.java), [CorpusStatisticsTest.java](../../../../codex-ir-core/src/test/java/codex/ir/corpus/CorpusStatisticsTest.java) (20 tests) |
| Immutable `CorpusSnapshot`, `IndexSnapshot` | [SnapshotSearchTest.java](../../../../codex-ir-core/src/test/java/codex/ir/search/SnapshotSearchTest.java) (4 tests) |
| Combined lexical + vector preprocessing pipeline | [Indexers.java](../../../../codex-ir-core/src/main/java/codex/ir/indexer/Indexers.java), [BatchIndexerTest.java](../../../../codex-ir-core/src/test/java/codex/ir/indexer/BatchIndexerTest.java) (7 tests) |
| Batch indexing with shared `CorpusSnapshot` for vector TF-IDF | IR-0.5 — [ir-0-5-batch-index-build.md](../../../../docs/knowledge/logs/core/ir-0-5-batch-index-build.md) |
| Binary, TF-IDF, BM25 ranking | [RankersTest.java](../../../../codex-ir-core/src/test/java/codex/ir/ranking/RankersTest.java) (12 tests) |
| BM25 configurable k1/b (constructor only) | [Bm25Ranker](../../../../codex-ir-core/src/main/java/codex/ir/ranking/Rankers.java#L230-L257) — class supports it; factory hardcodes defaults (k1=1.2, b=0.75) |
| `PreprocessedDocument` token artifact (IR-1) | [ir-1-preprocessed-document.md](../../../../docs/knowledge/logs/core/ir-1-preprocessed-document.md), [PreprocessedDocumentTest.java](../../../../codex-ir-core/src/test/java/codex/ir/indexer/PreprocessedDocumentTest.java) (11 tests) |
| `FieldAnalyzedDocument` (IR-2) preserving per-field token sequences | [ir-2-field-provenance.md](../../../../docs/knowledge/logs/core/ir-2-field-provenance.md), [FieldAnalyzedDocumentTest.java](../../../../codex-ir-core/src/test/java/codex/ir/indexer/FieldAnalyzedDocumentTest.java) (12 tests) |
| `Posting.fieldFrequencies` and `InvertedIndex.addFieldOccurrence` (IR-3) | [ir-3-field-aware-postings.md](../../../../docs/knowledge/logs/core/ir-3-field-aware-postings.md), [FieldAwarePostingsTest.java](../../../../codex-ir-core/src/test/java/codex/ir/indexer/FieldAwarePostingsTest.java) (10 tests) |
| Field-weighted lexical boosting via `FieldWeights` + `RankingContext` (IR-4) | [ir-4-field-aware-ranking.md](../../../../docs/knowledge/logs/core/ir-4-field-aware-ranking.md), [FieldAwareRankingTest.java](../../../../codex-ir-core/src/test/java/codex/ir/ranking/FieldAwareRankingTest.java) (10 tests) |
| Sparse document vectors over local vocabulary | [Vectorizer.java](../../../../codex-ir-core/src/main/java/codex/ir/vector/Vectorizer.java), [VectorStores.java](../../../../codex-ir-core/src/main/java/codex/ir/vector/store/VectorStores.java) |
| TF and TF-IDF document weighting | [WeightersTest.java](../../../../codex-ir-core/src/test/java/codex/ir/weight/WeightersTest.java), [DocumentWeighterTest.java](../../../../codex-ir-core/src/test/java/codex/ir/vector/DocumentWeighterTest.java) |
| Sparse cosine similarity (linear scan) | [Similarities.java](../../../../codex-ir-core/src/main/java/codex/ir/vector/Similarities.java), [SparseCosineSimilarityTest.java](../../../../codex-ir-core/src/test/java/codex/ir/vector/SparseCosineSimilarityTest.java) |
| `VectorSearcher` over `DocumentVectorStore` with threshold filter | [VectorSearcherTest.java](../../../../codex-ir-core/src/test/java/codex/ir/search/VectorSearcherTest.java) (4 tests) |
| Virtual-thread helpers (`VTExecutor`, `Debouncer`) | [VTExecutors.java](../../../../codex-ir-core/src/main/java/codex/ir/concurrent/VTExecutors.java), [DebouncerTest.java](../../../../codex-ir-core/src/test/java/codex/ir/concurrent/DebouncerTest.java) (9 tests) |
| Documents with structured fields, aggregated whole-document indexing | [DocumentPreprocessorTest.java](../../../../codex-ir-core/src/test/java/codex/ir/indexer/DocumentPreprocessorTest.java), [FieldAwareIndexingTest.java](../../../../codex-ir-core/src/test/java/codex/ir/indexer/FieldAwareIndexingTest.java) |

### 3.2 Core IR — implemented but partial

| Capability | Status | Evidence |
|---|---|---|
| Positions in postings | Stored; **not consumed at query time** | [Posting.java](../../../../codex-ir-core/src/main/java/codex/ir/indexer/Posting.java), [current-system.md](../../../../docs/knowledge/architecture/current-system.md) |
| BM25 k1/b parameter exposure | Class supports it, factory `Rankers.bm25(...)` does not | [Rankers.java](../../../../codex-ir-core/src/main/java/codex/ir/ranking/Rankers.java) |

### 3.3 Core IR — planned / proposed / unselected

| Capability | Source of intent | Status |
|---|---|---|
| Score explanation (per-term, per-field, applied boost) | IR-5 — [Plan01-Roadmap-Field-AwareIR.md](../../../../docs/tasks/core/Plan01-Roadmap-Field-AwareIR.md), [ir-4-field-aware-ranking.md](../../../../docs/knowledge/logs/core/ir-4-field-aware-ranking.md) | Candidate — not selected |
| Evaluation harness (qrels, precision@k, hit@k) | IR-6 — [Plan01-Roadmap-Field-AwareIR.md](../../../../docs/tasks/core/Plan01-Roadmap-Field-AwareIR.md) | Candidate — not selected |
| Field-specific query syntax (`title:java`) | IR-5+ in plan | Not started |
| BM25F (per-field lengths, per-field IDF) | Open choice — [candidate-capabilities.md](../../../../docs/knowledge/research/candidate-capabilities.md) | Not started |
| Field-aware vector indexing / BM25F for vectors | Open question — [candidate-capabilities.md](../../../../docs/knowledge/research/candidate-capabilities.md) | Not started |
| Phrase / proximity / passage search using stored positions | Research candidate | Not started |
| Top-K retrieval, pagination, early termination | Research candidate | Not started |
| Stemming, character n-grams, sub-word tokenization | Research candidate | Not started |
| Rank fusion, paragraph/document centroids, clustering, graph representations, concept extraction, extractive summarization | Research candidate — [candidate-capabilities.md](../../../../docs/knowledge/research/candidate-capabilities.md) | Not started |
| Dense vectors, embedding model port, ANN | IR-8 / research candidate | Not started |
| Hybrid retrieval (lexical + vector merge) | IR-9 / research candidate | Not started |
| Disk-backed corpus/index/vector/vocabulary | Architectural direction | Not started; gated by observability per [ADR-003.md](../../../../docs/adrs/ADR-003.md) |
| BM25 reference-value correctness, concurrent write/read stress tests | QA gap — [knowledge-reconciliation-2026-09.md](../../../../docs/engineering/agents/reviews/knowledge-reconciliation-2026-09.md) | Absent |
| Latency SLO, retrieval observability (stage timings, candidate counts, score components) | Identified gap — [myir-interview-deep-dive-notes.md](../../../../docs/engineering/agents/reports/myir-interview-deep-dive-notes.md) §"Additional senior-search-engineer topics" | Absent |

### 3.4 Web / Extraction — implemented and tested

| Capability | Evidence |
|---|---|
| URI canonicalization (fragment, lowercasing, default port, path normalize, query sort) | [UriCanonicalizersTest.java](../../../../codex-ir-web/src/test/java/codex/ir/canonicalizer/UriCanonicalizersTest.java) (12 tests) |
| Static HTML fetching via Jsoup | [WebPageFetchers.java](../../../../codex-ir-web/src/main/java/codex/ir/ingestion/crawler/WebPageFetchers.java) |
| URL classification across 14 `UrlType` values | [UrlClassifiersTest.java](../../../../codex-ir-web/src/test/java/codex/ir/ingestion/crawler/classifier/UrlClassifiersTest.java) (35 tests) |
| Page classification (generic + WordPress/WooCommerce) | [PageClassifiersTest.java](../../../../codex-ir-web/src/test/java/codex/ir/ingestion/crawler/classifier/PageClassifiersTest.java), [PageClassifiersJsoupDefaultTest.java](../../../../codex-ir-web/src/test/java/codex/ir/ingestion/crawler/classifier/PageClassifiersJsoupDefaultTest.java) (44 tests combined) |
| Composable URL filter predicates | [UrlFiltersTest.java](../../../../codex-ir-web/src/test/java/codex/ir/ingestion/crawler/filter/UrlFiltersTest.java) (11 tests) |
| Sitemap parsing (XML inline) | [SitemapParserTest](../../../../codex-ir-web/src/test/java/codex/ir/ingestion/crawler/internal/sitemap/SitemapParserTest.java) (13 tests) |
| Robots.txt parsing | [RobotsParserTest](../../../../codex-ir-web/src/test/java/codex/ir/ingestion/crawler/internal/sitemap/RobotsParserTest.java) (13 tests) |
| BFS site traversal with virtual threads | [SiteTraversalStrategy](../../../../codex-ir-web/src/main/java/codex/ir/ingestion/crawler/internal/traversal/SiteTraversalStrategy.java) |
| Sitemap-driven traversal with WordPress fallback paths | [SitemapSiteTraversalStrategy](../../../../codex-ir-web/src/main/java/codex/ir/ingestion/crawler/internal/sitemap/SitemapSiteTraversalStrategy.java) |
| Page metadata extraction (title, OG, Twitter Card, JSON-LD, headings, robots meta) | [PageMetadataExtractorsTest.java](../../../../codex-ir-web/src/test/java/codex/ir/ingestion/crawler/metadata/PageMetadataExtractorsTest.java) (26 tests combined) |
| Product detail extraction (JSON-LD, OG, HTML heuristics) | [ProductDetailExtractorsTest.java](../../../../codex-ir-web/src/test/java/codex/ir/ingestion/crawler/product/ProductDetailExtractorsTest.java) (46 tests combined) |
| Product card extraction (generic + WooCommerce) | [ProductCardExtractorsTest.java](../../../../codex-ir-web/src/test/java/codex/ir/ingestion/crawler/product/ProductCardExtractorsTest.java) (32 tests combined) |
| Product discovery end-to-end pipeline | [ProductDiscoverersTest.java](../../../../codex-ir-web/src/test/java/codex/ir/ingestion/crawler/product/ProductDiscoverersTest.java) (9 tests), [ProductDiscoveryCollectorsTest.java](../../../../codex-ir-web/src/test/java/codex/ir/ingestion/crawler/product/ProductDiscoveryCollectorsTest.java) (8 tests) |
| Discovery report writing (JSON) | [DiscoveryReportWritersTest.java](../../../../codex-ir-web/src/test/java/codex/ir/ingestion/crawler/product/DiscoveryReportWritersTest.java) (8 tests) |
| WebPage → Document mapper for IR ingestion | [Mappers.webPage()](../../../../codex-ir-web/src/main/java/codex/ir/ingestion/Mappers.java), [Ingestors.simple()](../../../../codex-ir-web/src/main/java/codex/ir/ingestion/Ingestors.java) |

### 3.5 Web / Extraction — implemented but partial or stub

| Capability | Status | Evidence |
|---|---|---|
| Dynamic (JavaScript-rendered) fetching via Playwright | **Stub** — `PlaywrightWebPageFetcher.dynamicHtml()` returns `Optional.empty()` | [README.md](../../../../README.md) §"Web Ingestion", [myir-state-of-the-art-2026-09.md](../../../../docs/engineering/agents/reports/myir-state-of-the-art-2026-09.md) §6.1 |
| HTTP integration test coverage | None — web tests use stub fetchers and inline HTML | [knowledge-reconciliation-2026-09.md](../../../../docs/engineering/agents/reviews/knowledge-reconciliation-2026-09.md) §"QA evidence assessment" |
| `JdkWebHttpFetcher.close()` resource lifecycle | Buggy — `Objects.isNull(httpClient)` is the wrong guard | [WebHttpFetchers.java:111-117](../../../../codex-ir-web/src/main/java/codex/ir/ingestion/crawler/fetcher/WebHttpFetchers.java) |
| Virtual-thread executor shutdown | `// todo: we should close this at the end of the life-cycle` | [WebHttpFetchers.java:52](../../../../codex-ir-web/src/main/java/codex/ir/ingestion/crawler/fetcher/WebHttpFetchers.java) |
| Sitemap-first crawl mode for site exporter | Not exposed; exporter uses BFS only | [README.md](../../../../README.md) §"Site Exporter" |

### 3.6 Application — implemented and tested

| Capability | Evidence |
|---|---|
| `codex.scraper.Main` — live crawl → index → lexical and vector search demo | [Main.java](../../../../codex-ir-app/src/main/java/codex/scraper/Main.java); crawl against `demo.dotcms.com`, 100 pages / depth 2 |
| `codex.scraper.DiscoveryRunner` — explicit URL / sitemap product discovery | [DiscoveryRunner.java](../../../../codex-ir-app/src/main/java/codex/scraper/DiscoveryRunner.java) |
| `codex.scraper.QuickDiscoveryRunner` — IDE wrapper hard-coded to `syjleathers.com` | [QuickDiscoveryRunner.java](../../../../codex-ir-app/src/main/java/codex/scraper/QuickDiscoveryRunner.java) |
| `SiteExporterCommand` — crawl → mirror → publish to PDF/Markdown/EPUB | [SiteExporterCommand.java](../../../../codex-ir-app/src/main/java/codex/apps/siteexporter/SiteExporterCommand.java) |
| Mirror contract: per-page HTML + `mirror-manifest.json` with portable paths | Phase 5 — [phase-5-manifest-hygiene.md](../../../../docs/knowledge/logs/site-exporter/phase-5-manifest-hygiene.md), [phase-5-fix-jackson-manifest-reader.md](../../../../docs/knowledge/logs/site-exporter/phase-5-fix-jackson-manifest-reader.md) |
| Asset discovery, download, and local link rewriting for PDF | Phase 6A, 6B — [phase-6a-asset-discovery-download.md](../../../../docs/knowledge/logs/site-exporter/phase-6a-asset-discovery-download.md), [phase-6b-html-link-rewriting.md](../../../../docs/knowledge/logs/site-exporter/phase-6b-html-link-rewriting.md) |
| `PublicationDriver` interface and `--format pdf|markdown|epub` | Phase 10.10 — [phase-10-10-publication-driver-refactor.md](../../../../docs/knowledge/logs/site-exporter/phase-10-10-publication-driver-refactor.md) |
| PDF rendering via OpenHTMLToPDF, merged with PDFBox | Phase 8/9/10 — [phase-8-pdf-renderer-port.md](../../../../docs/knowledge/logs/site-exporter/phase-8-pdf-renderer-port.md), [phase-10-pdf-assembly.md](../../../../docs/knowledge/logs/site-exporter/phase-10-pdf-assembly.md) |
| Markdown extraction (Jsoup) | Phase 10.9 — [phase-10-9-markdown-publication-writer.md](../../../../docs/knowledge/logs/site-exporter/phase-10-9-markdown-publication-writer.md) |
| EPUB 3 archive via `java.util.zip` | Phase 11 — [phase-11-epub-publication-driver.md](../../../../docs/knowledge/logs/site-exporter/phase-11-epub-publication-driver.md) |
| Resume from existing mirror (`--from-mirror`) | Phase 10.8 — [phase-10-8-resume-existing-mirror.md](../../../../docs/knowledge/logs/site-exporter/phase-10-8-resume-existing-mirror.md) |
| pdf2htmlEX reader extraction and reader-oriented HTML route | Phase 10.7/10.8 — [phase-10-7-pdf2htmlex-reader-extraction.md](../../../../docs/knowledge/logs/site-exporter/phase-10-7-pdf2htmlex-reader-extraction.md), [phase-10-8-pdf2htmlex-reader-route.md](../../../../docs/knowledge/logs/site-exporter/phase-10-8-pdf2htmlex-reader-route.md) |
| Virtual-thread `PipelineExecutionPolicy` | [VirtualThreadPipelineExecutionPolicy](../../../../codex-ir-app/src/main/java/codex/apps/siteexporter/VirtualThreadPipelineExecutionPolicy.java) |

### 3.7 Application — implemented but partial

| Capability | Status | Evidence |
|---|---|---|
| EPUB validation with `epubcheck`, real-reader validation | Not yet validated | [README.md](../../../../README.md) §"Site Exporter" — Current limitations |
| Markdown and EPUB visual fidelity | "Prioritize readable text over complete visual fidelity" | [README.md](../../../../README.md) §"Current Limitations and Next Directions" |
| Sitemap-first crawl mode in the site exporter CLI | Not exposed; `--url` uses BFS | [SiteExporterCommand.java](../../../../codex-ir-app/src/main/java/codex/apps/siteexporter/SiteExporterCommand.java), [README.md](../../../../README.md) §"Site Exporter" |
| Dynamic-rendering crawl mode in the site exporter CLI | Not exposed | Same as above |

### 3.8 Application — not implemented

| Capability | Source of intent | Status |
|---|---|---|
| Crawl + index + search user-facing site search | Architecture enables it (`Main.java` does it as a demo) | Not wired as an application; site exporter and IR engine are intentionally independent ([current-system.md](../../../../docs/knowledge/architecture/current-system.md)) |

---

## 4. Roadmap Assessment

### 4.1 Committed roadmap

The committed roadmap is empty. [ROADMAP.md](../../../../docs/roadmap/ROADMAP.md) lists two items, both candidates:

| Item | Outcome | Status | Reference |
|---|---|---|---|
| IR-5 — Score Explanation | Explain ranking signals, weights/boosts, contributions, final score | Candidate — not selected | [candidate-capabilities.md](../../../../docs/knowledge/research/candidate-capabilities.md) |
| IR-6 — Evaluation Harness | Evaluate retrieval-quality changes using judged queries and metrics | Candidate — not selected | Same |

The roadmap document explicitly states: *"They are not implicitly scheduled by their presence there."* This is a deliberate "no next iteration selected" posture, not an omission ([ROADMAP.md](../../../../docs/roadmap/ROADMAP.md) §"Non-committed research and architectural direction").

### 4.2 Recently completed (canonical evidence per item)

| Item | Status | Evidence |
|---|---|---|
| IR-0 — Read/Write Boundary | Complete | [ir-0-read-write-boundary.md](../../../../docs/knowledge/logs/core/ir-0-read-write-boundary.md); [SnapshotSearchTest.java](../../../../codex-ir-core/src/test/java/codex/ir/search/SnapshotSearchTest.java) |
| IR-0.5 — Batch Index Build | Complete | [ir-0-5-batch-index-build.md](../../../../docs/knowledge/logs/core/ir-0-5-batch-index-build.md); [BatchIndexerTest.java](../../../../codex-ir-core/src/test/java/codex/ir/indexer/BatchIndexerTest.java) |
| IR-1 — PreprocessedDocument | Complete | [ir-1-preprocessed-document.md](../../../../docs/knowledge/logs/core/ir-1-preprocessed-document.md); [PreprocessedDocumentTest.java](../../../../codex-ir-core/src/test/java/codex/ir/indexer/PreprocessedDocumentTest.java) |
| IR-2 — Field Provenance | Complete | [ir-2-field-provenance.md](../../../../docs/knowledge/logs/core/ir-2-field-provenance.md); [FieldAnalyzedDocumentTest.java](../../../../codex-ir-core/src/test/java/codex/ir/indexer/FieldAnalyzedDocumentTest.java) |
| IR-3 — Field-Aware Postings | Complete | [ir-3-field-aware-postings.md](../../../../docs/knowledge/logs/core/ir-3-field-aware-postings.md); [FieldAwarePostingsTest.java](../../../../codex-ir-core/src/test/java/codex/ir/indexer/FieldAwarePostingsTest.java) |
| IR-4 — Field-Aware Ranking | Complete | [ir-4-field-aware-ranking.md](../../../../docs/knowledge/logs/core/ir-4-field-aware-ranking.md); [FieldAwareRankingTest.java](../../../../codex-ir-core/src/test/java/codex/ir/ranking/FieldAwareRankingTest.java) |
| Site Exporter Phases 3–11 | Complete | 17 CKF logs in [docs/knowledge/logs/site-exporter](../../../../docs/knowledge/logs/site-exporter) |

### 4.3 Inferred near-term direction (proposed, not committed)

The [candidate map](../../../../docs/knowledge/research/candidate-capabilities.md) and the [Plan01 historical delivery plan](../../../../docs/tasks/core/Plan01-Roadmap-Field-AwareIR.md) sketch a non-committed sequence:

```text
IR-5  Score Explanation         (observability groundwork)
IR-6  Evaluation Harness         (empirical-quality groundwork)
IR-7  N-grams                    (research)
IR-8  Dense vectors              (research)
IR-9  Hybrid retrieval           (research)
IR-10 Document centroids         (research)
IR-11 Site centroids             (research)
IR-12 Summaries                  (research)
IR-13 Syntax trees               (research)
```

Plus architectural direction: **disk-backed corpus, index, vocabulary, and vector store implementations when evidence justifies them** ([candidate-capabilities.md](../../../../docs/knowledge/research/candidate-capabilities.md)).

The two near/mid-term retrieval candidates are: stemming, character n-grams/sub-word tokenization, phrase/proximity retrieval using stored positions, BM25 parameter exposure/tuning, field-aware vector indexing.

The knowledge bundle warns explicitly that *"IR-5 is observability groundwork"* and *"IR-6 is empirical-quality groundwork; it should allow retrieval changes to be compared against judged queries rather than only tested for formula correctness. Neither item is selected as the next iteration by this classification."* ([candidate-capabilities.md](../../../../docs/knowledge/research/candidate-capabilities.md))

### 4.4 Web/extraction module has no committed roadmap

The web module has 38 task files under [docs/tasks/apps/information-extraction/](../../../../docs/tasks/apps/information-extraction/) (Task1..Task38) but no corresponding CKF engineering logs, no knowledge entries, and no roadmap line. The web module's current capability is documented as a curated historical representation, not a roadmap ([extraction-and-applications.md](../../../../docs/knowledge/web/extraction-and-applications.md)).

---

## 5. Documentation Assessment

### 5.1 Sources of truth (current)

| Source | Path | Status |
|---|---|---|
| Root README | [README.md](../../../../README.md) | Comprehensive, current, includes field aggregation, snapshot boundary, site-exporter CLI, known limitations |
| Knowledge index (CKF) | [docs/knowledge/index.md](../../../../docs/knowledge/index.md) | Navigable; covers IR-0 through IR-4 plus site-exporter logs |
| CKF profile | [docs/knowledge/meta/ckf-profile.md](../../../../docs/knowledge/meta/ckf-profile.md) | Documents the OKF/CKF frontmatter conventions |
| Current system knowledge | [docs/knowledge/architecture/current-system.md](../../../../docs/knowledge/architecture/current-system.md) | Authoritative on boundaries and "intentional independence" of the site exporter from the IR engine |
| Current ADR (CKF) | [docs/knowledge/decisions/adr-005-field-aware-indexing.md](../../../../docs/knowledge/decisions/adr-005-field-aware-indexing.md) | Updated to reflect IR-3/IR-4 completion |
| CKF engineering logs — core | [docs/knowledge/logs/core/](../../../../docs/knowledge/logs/core) | Complete for IR-0 through IR-4 |
| CKF engineering logs — site exporter | [docs/knowledge/logs/site-exporter/](../../../../docs/knowledge/logs/site-exporter) | 17 entries through Phase 11 |
| Web extraction knowledge | [docs/knowledge/web/extraction-and-applications.md](../../../../docs/knowledge/web/extraction-and-applications.md) | Curated capability overview |
| Project context | [docs/PROJECT.md](../../../../docs/PROJECT.md) | Current |
| Workspace guide | [docs/OSK.md](../../../../docs/OSK.md) | Current; classifies by purpose |
| Agent guides | [AGENTS.md](../../../../AGENTS.md), [CLAUDE.md](../../../../CLAUDE.md) | Reconciled; entry points match actual classes |
| Module READMEs | [codex-ir-core/README.md](../../../../codex-ir-core/README.md), [codex-ir-web/README.md](../../../../codex-ir-web/README.md), [codex-ir-app/README.md](../../../../codex-ir-app/README.md) | Current |
| Candidate map | [docs/knowledge/research/candidate-capabilities.md](../../../../docs/knowledge/research/candidate-capabilities.md) | Current; explicitly non-committed |
| Roadmap | [docs/roadmap/ROADMAP.md](../../../../docs/roadmap/ROADMAP.md) | Placeholder by design; lists candidates only |
| package-info.java files | Every package | Complete coverage through IR-4 |
| `application/source` code | `codex-ir-core/src/main/java/...`, `codex-ir-web/...`, `codex-ir-app/...` | Authoritative |
| Test suite | 1,022 tests, 0 failures | Authoritative on what is mechanically correct |

### 5.2 Historical / partially superseded

| Document | Status | Evidence |
|---|---|---|
| [docs/Future-Forward.md](../../../../docs/Future-Forward.md) | **Stale.** A 2026-09-04 banner at the top now marks it historical. Body still claims field-aware docs, field weighting, and web crawling are future. All three are now implemented. | Banner; vs. [ir-4-field-aware-ranking.md](../../../../docs/knowledge/logs/core/ir-4-field-aware-ranking.md) |
| [docs/adrs/ADR-004.md](../../../../docs/adrs/ADR-004.md) | **Partially superseded.** "Not yet supported" table marks field weighting as not implemented. A 2026-09-04 note records the partial supersession. Body otherwise still useful as history. | ADR-004 §"What is intentionally not supported yet" |
| [docs/tasks/core/Plan01-Roadmap-Field-AwareIR.md](../../../../docs/tasks/core/Plan01-Roadmap-Field-AwareIR.md) | **Historical execution plan.** Banner marks it as such; IR-0 through IR-4 are done. IR-5/IR-6 still unselected. | Plan01 banner |
| [docs/adrs/ADR-001.md](../../../../docs/adrs/ADR-001.md), [ADR-002.md](../../../../docs/adrs/ADR-002.md), [ADR-003.md](../../../../docs/adrs/ADR-003.md) | **Current.** Each documents a still-valid architectural choice. | Contents |
| [docs/adrs/ADR-005.md](../../../../docs/adrs/ADR-005.md) | **Forwarding stub** pointing to the CKF version. Do not duplicate. | ADR-005 |
| [docs/adr/](../../../../docs/adr/) | **New ADR location per OSK.** README explains the policy; no ADRs filed here yet. | [docs/adr/README.md](../../../../docs/adr/README.md) |
| [docs/reports/modularization-plan.md](../../../../docs/reports/modularization-plan.md) | **Historical / already implemented.** Describes the modularization as a "proposed design plan." The three-module layout it described is what the repo now has. | Plan body |
| [docs/reports/core/ENGINEERING_LOG.md](../../../../docs/reports/core/ENGINEERING_LOG.md), [docs/apps/site-exporter/ENGINEERING_LOG.md](../../../../docs/apps/site-exporter/ENGINEERING_LOG.md) | **Historical link indexes.** The CKF bundle is the canonical evidence location. | Both files note "this historical path is retained for backwards compatibility" |
| [codex-ir-app/README.md](../../../../codex-ir-app/README.md) | **Stale entry point name.** Lists `codex.Main`, but the actual class is `codex.scraper.Main`. CLAUDE.md is corrected. | `codex-ir-app/README.md` §"Entry Points" |
| [docs/engineering/ENGINEERING_LOG.md](../../../../docs/engineering/ENGINEERING_LOG.md) | **Current compact index**; one real report (this one), one review, one interview brief. Scaffolding is in place. | File contents |
| [docs/engineering/agents/reports/myir-state-of-the-art-2026-09.md](../../../../docs/engineering/agents/reports/myir-state-of-the-art-2026-09.md) | **Companion assessment** produced on the same date by another agent. Largely overlapping conclusions; this report is independently produced. | File contents |
| [docs/engineering/agents/reports/myir-interview-deep-dive-notes.md](../../../../docs/engineering/agents/reports/myir-interview-deep-dive-notes.md) | **Interview-style brief.** Authoritative as evidence on which claims are testable; not a roadmap. | File contents |
| [docs/engineering/agents/reviews/knowledge-reconciliation-2026-09.md](../../../../docs/engineering/agents/reviews/knowledge-reconciliation-2026-09.md) | **Current.** The reconciliation that updated ADR-004 and ADR-005 CKF versions. | File contents |
| `docs/tasks/apps/information-extraction/` | **38 task files, no CKF counterpart.** Historical task prompts. | [extraction-and-applications.md](../../../../docs/knowledge/web/extraction-and-applications.md) |

### 5.3 Conflicts and reconciliation

- **README vs. Future-Forward vs. ADR-004 vs. ADR-005** — Root README is current; Future-Forward body is stale; ADR-004 has a 2026-09-04 partial-supersession note; ADR-005 is a forwarding stub. A reader who follows ADR-005 → CKF decision file gets the current view.
- **`ROADMAP.md` vs. `Plan01`** — `ROADMAP.md` is the canonical place for committed direction (currently empty/candidates). `Plan01` is a historical execution plan. They should not be conflated.
- **App README vs. CLAUDE.md vs. AGENTS.md** — CLAUDE.md/AGENTS.md name `codex.scraper.Main`; `codex-ir-app/README.md` still names `codex.Main` (incorrect).

---

## 6. Technical Risks

### 6.1 Correctness / reliability

1. **JdkWebHttpFetcher `close()` is broken.** The condition is `Objects.isNull(httpClient)` — the wrong guard; the field is never null, so the body never runs. Resource leak on long-running crawls. [WebHttpFetchers.java:111-117](../../../../codex-ir-web/src/main/java/codex/ir/ingestion/crawler/fetcher/WebHttpFetchers.java)
2. **Virtual-thread executor never shut down.** The per-task executor is constructed but there is no shutdown path. Marked `// todo`. [WebHttpFetchers.java:52](../../../../codex-ir-web/src/main/java/codex/ir/ingestion/crawler/fetcher/WebHttpFetchers.java)
3. **BM25 k1/b not exposed via factory.** `Bm25Ranker` has a 4-arg constructor (corpus, index, k1, b) but `Rankers.bm25(corpus, index)` always uses defaults (1.2, 0.75). Tuning requires a direct constructor call. [Rankers.java:81-83, 230-257](../../../../codex-ir-core/src/main/java/codex/ir/ranking/Rankers.java)
4. **TfIdfDocumentWeighter takes a live `InvertedIndex` (not a snapshot)** in some call sites. Under concurrent indexing, DF values can diverge from the snapshot used by `TfIdfRanker`. Identified as a latent consistency risk. [knowledge-reconciliation-2026-09.md](../../../../docs/engineering/agents/reviews/knowledge-reconciliation-2026-09.md) §"Maintenance backlog"
5. **No concurrent write/read test on `InvertedIndex` and `Corpus`.** All snapshot tests are sequential. [knowledge-reconciliation-2026-09.md](../../../../docs/engineering/agents/reviews/knowledge-reconciliation-2026-09.md) §"QA evidence assessment"
6. **Playwright dynamic fetcher is a no-op stub.** `PlaywrightWebPageFetcher.dynamicHtml()` returns `Optional.empty()` unconditionally. JavaScript-rendered pages are not supported; this is not surfaced in the CLI or root README. [README.md](../../../../README.md) §"Web Ingestion"

### 6.2 Performance / scale

7. **`VectorSearcher` is O(N) linear scan** over all stored vectors. No ANN index structure exists or is planned in the current scope. Adequate for hundreds; does not scale. [VectorSearcher.java](../../../../codex-ir-core/src/main/java/codex/ir/search/VectorSearcher.java)
8. **Positions stored in every posting** but never consumed. Memory overhead with no current payoff. [Posting.java](../../../../codex-ir-core/src/main/java/codex/ir/indexer/Posting.java), [current-system.md](../../../../docs/knowledge/architecture/current-system.md)
9. **No top-K, pagination, or early termination.** Both searchers collect all candidates, sort in memory, and return the full list. [Future-Forward.md](../../../../docs/Future-Forward.md) §1.5
10. **All core storage in memory** by design. Documented to remain so until observability justifies persistence (ADR-003). State is lost on restart. [ADR-003.md](../../../../docs/adrs/ADR-003.md)

### 6.3 Supply-chain / dependency

11. **`openhtmltopdf-pdfbox` is archived upstream** (last release 2022). No security patches. The site-exporter PDF path depends on it. [knowledge-reconciliation-2026-09.md](../../../../docs/engineering/agents/reviews/knowledge-reconciliation-2026-09.md) §"Maintenance backlog"
12. **No CI, no pre-commit, no Makefile.** Build correctness is only verified manually via `mvn test`. [CLAUDE.md](../../../../CLAUDE.md) §"Documentation Discipline"

### 6.4 Evaluation

13. **No relevance measurement exists.** No qrels, no precision/recall, no MRR, no nDCG, no A/B. Without IR-6, every ranking change is unmeasurable. [myir-interview-deep-dive-notes.md](../../../../docs/engineering/agents/reports/myir-interview-deep-dive-notes.md) §2, [Plan01-Roadmap-Field-AwareIR.md](../../../../docs/tasks/core/Plan01-Roadmap-Field-AwareIR.md) §IR-6
14. **No retrieval observability** beyond `SearchResult` score and matched terms. No query traces, no candidate counts, no per-stage timings. [myir-interview-deep-dive-notes.md](../../../../docs/engineering/agents/reports/myir-interview-deep-dive-notes.md) §"Additional senior-search-engineer topics"

### 6.5 Architectural consistency

15. **Field-aware vector path is deliberately unresolved.** Sparse vectors are computed from aggregated whole-document content; `VectorIndexer` does not consume `FieldAnalyzedDocument`. Whether it should is recorded as an open research question, not an implied future commitment. [candidate-capabilities.md](../../../../docs/knowledge/research/candidate-capabilities.md) §"Open design question"
16. **Site-exporter and IR engine are intentionally independent.** No integration path exists. Treating this as a "missing feature" misreads the architecture. [current-system.md](../../../../docs/knowledge/architecture/current-system.md)
17. **Web module "default" carries WordPress/WooCommerce assumptions** (URL classifier, sitemap fallback paths). Acceptable as application specialization, but a caller using `wordpressWooCommerceDefaultWeb()` as the *default* gets this silently. [extraction-and-applications.md](../../../../docs/knowledge/web/extraction-and-applications.md)

### 6.6 Documentation drift

18. **`docs/Future-Forward.md` body is stale** despite a 2026-09-04 banner. A new reader who skips the banner will be misled.
19. **`docs/adrs/ADR-004.md` "not yet supported" table is partially stale** despite a partial-supersession note.
20. **`codex-ir-app/README.md` lists `codex.Main`** as the entry point; actual class is `codex.scraper.Main`.
21. **`docs/roadmap/ROADMAP.md` is a placeholder.** A new reader who treats it as a complete roadmap will see "no next iteration" and stop.

---

## 7. Open Questions (to ask before implementing new work)

These are questions to put to the team before committing to new code, derived directly from the knowledge bundle, the existing reports, and the documented risks.

### 7.1 Strategy

1. **Which is next: IR-5 (Score Explanation) or IR-6 (Evaluation Harness)?** Score explanation helps debug individual queries; the evaluation harness measures ranking quality across many queries. The team has not selected either. [ROADMAP.md](../../../../docs/roadmap/ROADMAP.md), [candidate-capabilities.md](../../../../docs/knowledge/research/candidate-capabilities.md)
2. **What is the criterion for "an improvement" in ranking quality?** Without a judged corpus, no ranking change can be claimed as an improvement. [Plan01-Roadmap-Field-AwareIR.md](../../../../docs/tasks/core/Plan01-Roadmap-Field-AwareIR.md) §IR-6 ("prevent imaginary improvements")
3. **Is the site-exporter / IR-engine independence an architectural choice to preserve, or is site-search a near-term product goal?** [current-system.md](../../../../docs/knowledge/architecture/current-system.md)
4. **Is WooCommerce/WordPress specialization a permanent product direction, or a transitional demo?** [extraction-and-applications.md](../../../../docs/knowledge/web/extraction-and-applications.md)
5. **Should `docs/Future-Forward.md` be archived or rewritten?** The body contradicts the current state.

### 7.2 IR engineering

6. **Should field-aware vector indexing be added (BM25F-style or field-weighted cosine)?** Recorded as an open design question, not a commitment. [candidate-capabilities.md](../../../../docs/knowledge/research/candidate-capabilities.md) §"Open design question"
7. **Which positional capability — phrase, proximity, passage, or snippets — would justify query-time use of stored positions?** Currently positions are stored but unused. [current-system.md](../../../../docs/knowledge/architecture/current-system.md)
8. **Should `Rankers.bm25(corpus, index, k1, b)` be added to the public factory?** Tuning currently requires a direct constructor call. [Rankers.java](../../../../codex-ir-core/src/main/java/codex/ir/ranking/Rankers.java)
9. **Should the `TfIdfDocumentWeighter`'s index access be snapshot-isolated like the ranker?** Latent consistency risk under concurrent indexing. [knowledge-reconciliation-2026-09.md](../../../../docs/engineering/agents/reviews/knowledge-reconciliation-2026-09.md) §"Maintenance backlog"
10. **Is hybrid lexical/vector ranking in scope?** Required for any "vector wins" or "lexical wins" product decision. Not implemented. [myir-interview-deep-dive-notes.md](../../../../docs/engineering/agents/reports/myir-interview-deep-dive-notes.md) §3

### 7.3 Web / extraction

11. **Should the `JdkWebHttpFetcher.close()` bug and the executor shutdown be fixed before further crawler work?** [WebHttpFetchers.java](../../../../codex-ir-web/src/main/java/codex/ir/ingestion/crawler/fetcher/WebHttpFetchers.java)
12. **Should the Playwright stub be implemented or removed?** A no-op stub is misleading. [README.md](../../../../README.md) §"Web Ingestion"
13. **Should the site exporter expose a sitemap-first mode and a dynamic-render mode in the CLI?** Currently only BFS is wired. [README.md](../../../../README.md) §"Site Exporter"
14. **Should the web module receive a CKF engineering-log trail?** 38 task files exist; no CKF counterpart. [extraction-and-applications.md](../../../../docs/knowledge/web/extraction-and-applications.md)

### 7.4 App / dependencies

15. **Should `openhtmltopdf-pdfbox` be replaced or remain?** Library is archived; PDF rendering is a stated feature. [knowledge-reconciliation-2026-09.md](../../../../docs/engineering/agents/reviews/knowledge-reconciliation-2026-09.md) §"Maintenance backlog"
16. **Should EPUB output be validated with `epubcheck` before claiming it is a deliverable?** Currently unvalidated. [README.md](../../../../README.md) §"Current Limitations"
17. **Should a CI configuration (GitHub Actions, etc.) be added?** No CI today. [CLAUDE.md](../../../../CLAUDE.md) §"Documentation Discipline"

### 7.5 Documentation / process

18. **Should `docs/roadmap/ROADMAP.md` be reconciled with `docs/tasks/core/Plan01-Roadmap-Field-AwareIR.md`?** Roadmap document is a placeholder; the plan is a historical execution log. [ROADMAP.md](../../../../docs/roadmap/ROADMAP.md), [Plan01-Roadmap-Field-AwareIR.md](../../../../docs/tasks/core/Plan01-Roadmap-Field-AwareIR.md)
19. **Should `docs/reports/modularization-plan.md` be moved to `docs/knowledge/` as historical context, or removed?** It is already implemented; reading it as a plan is misleading.
20. **Should the `package-info.java` policy be re-checked after IR-4?** All packages are documented; the next change should preserve the convention.
21. **Should we add a `package-info.java` for `codex.apps.siteexporter` and the published exports of `codex.ir.app`?** CLAUDE.md notes that `codex.apps.siteexporter` is opened to Jackson via `module-info.java`; whether the package itself has package-level documentation should be verified.

---

## 8. Recommended Next Steps

Ordered by leverage and risk reduction. Each item names the smallest coherent unit of work and the evidence it would produce.

### 8.1 Quick wins (one task each, low risk, immediate value)

- **Q1. Fix `JdkWebHttpFetcher.close()`.** Replace `Objects.isNull(httpClient)` with the correct guard; add a unit test that calls `close()` and asserts shutdown. Closes a known resource-leak vector. [WebHttpFetchers.java](../../../../codex-ir-web/src/main/java/codex/ir/ingestion/crawler/fetcher/WebHttpFetchers.java)
- **Q2. Shut down the virtual-thread executor in `JdkWebHttpFetcher`.** Resolve the `// todo`. Same test as Q1. [WebHttpFetchers.java](../../../../codex-ir-web/src/main/java/codex/ir/ingestion/crawler/fetcher/WebHttpFetchers.java)
- **Q3. Document the Playwright stub in the CLI and root README.** A `dynamicHtml()` that always returns empty is a footgun. [README.md](../../../../README.md) §"Web Ingestion"
- **Q4. Expose `Rankers.bm25(corpus, index, k1, b)`.** Add a 4-arg factory overload, mirror `tfIdf` ergonomics, and add a focused test on a known reference value. [Rankers.java](../../../../codex-ir-core/src/main/java/codex/ir/ranking/Rankers.java)
- **Q5. Correct the entry point in `codex-ir-app/README.md`.** Replace `codex.Main` with `codex.scraper.Main`. [codex-ir-app/README.md](../../../../codex-ir-app/README.md)

### 8.2 Strategy decisions (require human planning)

- **S1. Select IR-5 *or* IR-6 as the next iteration.** Without a selected target, the field-aware path stalls. The deciding question is whether the team needs to debug a few specific queries (IR-5) or measure cross-query quality (IR-6). [ROADMAP.md](../../../../docs/roadmap/ROADMAP.md), [Plan01-Roadmap-Field-AwareIR.md](../../../../docs/tasks/core/Plan01-Roadmap-Field-AwareIR.md) §IR-5, §IR-6
- **S2. Decide on the site-exporter / IR-engine relationship.** Confirm intentional independence, or specify an integration slice. [current-system.md](../../../../docs/knowledge/architecture/current-system.md)
- **S3. Decide on `openhtmltopdf-pdfbox`.** Keep, replace (e.g. Flying Saucer fork, OpenPDF), or remove PDF from the supported formats.

### 8.3 Substantive engineering slices (one per slice)

- **E1. Build a relevance evaluation harness (IR-6).** Fixtures (small frozen crawl-derived corpus), queries with graded judgments, precision@k, hit@k, MRR, nDCG. This unblocks every future ranking claim. [Plan01-Roadmap-Field-AwareIR.md](../../../../docs/tasks/core/Plan01-Roadmap-Field-AwareIR.md) §IR-6
- **E2. Add score explanation (IR-5).** Per-term and per-field contribution breakdown; sums to final score; works with TF-IDF and BM25. Builds on IR-4. [Plan01-Roadmap-Field-AwareIR.md](../../../../docs/tasks/core/Plan01-Roadmap-Field-AwareIR.md) §IR-5
- **E3. Add a snapshot-isolated `DocumentWeighter` path.** Mirror the ranker's snapshot boundary in the weighter; add a concurrent read/write test. [knowledge-reconciliation-2026-09.md](../../../../docs/engineering/agents/reviews/knowledge-reconciliation-2026-09.md) §"Maintenance backlog"
- **E4. Add real HTTP integration coverage.** One end-to-end test against a deterministic local server (or carefully recorded fixture). [knowledge-reconciliation-2026-09.md](../../../../docs/engineering/agents/reviews/knowledge-reconciliation-2026-09.md) §"Maintenance backlog"
- **E5. Backfill CKF engineering logs for the web/extraction module.** Either write logs for the major capability phases or move selected task files into the CKF bundle. Avoid transcribing all 38 task files. [extraction-and-applications.md](../../../../docs/knowledge/web/extraction-and-applications.md)

### 8.4 Documentation maintenance (each independently useful)

- **D1. Archive `docs/Future-Forward.md` body** and keep only the supersession banner pointing to the canonical knowledge. Today the body contradicts the implementation.
- **D2. Update `docs/adrs/ADR-004.md`** to fully reflect the IR-3/IR-4 status (the current partial-supersession note can be made definitive).
- **D3. Reconcile `docs/roadmap/ROADMAP.md` and `docs/tasks/core/Plan01-Roadmap-Field-AwareIR.md`.** The roadmap should either link to the plan as historical context or the plan should be moved out of `docs/tasks/`.
- **D4. Add a one-line link in `docs/knowledge/index.md` to this report and to the existing companion report** to make the September 2026 assessment discoverable.
- **D5. Decide what to do with `docs/reports/modularization-plan.md`** — move to a historical folder or annotate as already-implemented.

### 8.5 Do not do (out of scope for the current stage)

- Do not introduce persistence, dense vectors, or hybrid retrieval before the evaluation harness exists. The team's own roadmap and reports identify the evaluation gap as the highest-leverage blocker.
- Do not rewrite `Future-Forward.md`'s body — it is historical; preserve it as such.
- Do not touch `pom.xml` without explicit request. [AGENTS.md](../../../../AGENTS.md) §"Coding conventions"
- Do not implement BM25F, field-aware vector indexing, or query-time positional search without a measured baseline. The candidate map warns explicitly that these are research directions, not commitments. [candidate-capabilities.md](../../../../docs/knowledge/research/candidate-capabilities.md)

---

## 9. Evidence Index

| Area | Key paths |
|---|---|
| IR engine root | [codex-ir-core/src/main/java/codex/ir/](../../../../codex-ir-core/src/main/java/codex/ir) |
| Field-aware ranking | [Ranker.java](../../../../codex-ir-core/src/main/java/codex/ir/ranking/Ranker.java), [FieldWeights.java](../../../../codex-ir-core/src/main/java/codex/ir/ranking/FieldWeights.java), [RankingContext.java](../../../../codex-ir-core/src/main/java/codex/ir/ranking/RankingContext.java) |
| BM25 | [Rankers.java](../../../../codex-ir-core/src/main/java/codex/ir/ranking/Rankers.java) |
| Indexing pipeline | [Indexers.java](../../../../codex-ir-core/src/main/java/codex/ir/indexer/Indexers.java), [InvertedIndex.java](../../../../codex-ir-core/src/main/java/codex/ir/indexer/InvertedIndex.java), [InvertedIndexes.java](../../../../codex-ir-core/src/main/java/codex/ir/indexer/InvertedIndexes.java) |
| Searchers | [SimpleSearcher.java](../../../../codex-ir-core/src/main/java/codex/ir/search/SimpleSearcher.java), [VectorSearcher.java](../../../../codex-ir-core/src/main/java/codex/ir/search/VectorSearcher.java) |
| Vector store | [VectorStores.java](../../../../codex-ir-core/src/main/java/codex/ir/vector/store/VectorStores.java) |
| Virtual-thread executor | [VTExecutors.java](../../../../codex-ir-core/src/main/java/codex/ir/concurrent/VTExecutors.java) |
| HTTP fetcher (bug) | [WebHttpFetchers.java](../../../../codex-ir-web/src/main/java/codex/ir/ingestion/crawler/fetcher/WebHttpFetchers.java) |
| WebPage ingestion | [WebPage.java](../../../../codex-ir-web/src/main/java/codex/ir/ingestion/WebPage.java), [Mappers.java](../../../../codex-ir-web/src/main/java/codex/ir/ingestion/Mappers.java), [Ingestors.java](../../../../codex-ir-web/src/main/java/codex/ir/ingestion/Ingestors.java) |
| URL/page classification | [UrlClassifiers.java](../../../../codex-ir-web/src/main/java/codex/ir/ingestion/crawler/classifier/UrlClassifiers.java), [PageClassifiers.java](../../../../codex-ir-web/src/main/java/codex/ir/ingestion/crawler/classifier/PageClassifiers.java) |
| Product discovery | [ProductDiscoverers.java](../../../../codex-ir-web/src/main/java/codex/ir/ingestion/crawler/product/ProductDiscoverers.java) |
| Sitemap/Robots | [SitemapSiteTraversalStrategy](../../../../codex-ir-web/src/main/java/codex/ir/ingestion/crawler/internal/sitemap/SitemapSiteTraversalStrategy.java) |
| App entry points | [Main.java](../../../../codex-ir-app/src/main/java/codex/scraper/Main.java), [DiscoveryRunner.java](../../../../codex-ir-app/src/main/java/codex/scraper/DiscoveryRunner.java), [QuickDiscoveryRunner.java](../../../../codex-ir-app/src/main/java/codex/scraper/QuickDiscoveryRunner.java) |
| Site exporter | [SiteExporterCommand.java](../../../../codex-ir-app/src/main/java/codex/apps/siteexporter/SiteExporterCommand.java) |
| Root READMEs and guides | [README.md](../../../../README.md), [AGENTS.md](../../../../AGENTS.md), [CLAUDE.md](../../../../CLAUDE.md), [docs/PROJECT.md](../../../../docs/PROJECT.md), [docs/OSK.md](../../../../docs/OSK.md) |
| CKF knowledge bundle | [docs/knowledge/index.md](../../../../docs/knowledge/index.md), [docs/knowledge/architecture/current-system.md](../../../../docs/knowledge/architecture/current-system.md), [docs/knowledge/web/extraction-and-applications.md](../../../../docs/knowledge/web/extraction-and-applications.md), [docs/knowledge/research/candidate-capabilities.md](../../../../docs/knowledge/research/candidate-capabilities.md), [docs/knowledge/decisions/adr-005-field-aware-indexing.md](../../../../docs/knowledge/decisions/adr-005-field-aware-indexing.md) |
| Roadmap and plans | [docs/roadmap/ROADMAP.md](../../../../docs/roadmap/ROADMAP.md), [docs/roadmap/future/README.md](../../../../docs/roadmap/future/README.md), [docs/tasks/core/Plan01-Roadmap-Field-AwareIR.md](../../../../docs/tasks/core/Plan01-Roadmap-Field-AwareIR.md) |
| ADRs | [docs/adrs/ADR-001.md](../../../../docs/adrs/ADR-001.md) … [docs/adrs/ADR-005.md](../../../../docs/adrs/ADR-005.md) |
| Engineering evidence | [docs/engineering/ENGINEERING_LOG.md](../../../../docs/engineering/ENGINEERING_LOG.md), [docs/engineering/agents/reports/myir-state-of-the-art-2026-09.md](../../../../docs/engineering/agents/reports/myir-state-of-the-art-2026-09.md), [docs/engineering/agents/reports/myir-interview-deep-dive-notes.md](../../../../docs/engineering/agents/reports/myir-interview-deep-dive-notes.md), [docs/engineering/agents/reviews/knowledge-reconciliation-2026-09.md](../../../../docs/engineering/agents/reviews/knowledge-reconciliation-2026-09.md) |
| Module READMEs | [codex-ir-core/README.md](../../../../codex-ir-core/README.md), [codex-ir-web/README.md](../../../../codex-ir-web/README.md), [codex-ir-app/README.md](../../../../codex-ir-app/README.md) |

---

*Report produced 2026-09-04. Primary evidence source: repository working tree, source code, test suite, and build output. Documentation consulted as secondary evidence and cross-referenced.*
