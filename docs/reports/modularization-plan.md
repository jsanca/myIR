# myIR Modularization Plan — Maven + Java Jigsaw Modules

## Status

Proposed — design plan. No implementation yet.

## Context

myIR has grown beyond a simple IR core. Before implementing the sitemap-based traversal strategy and WordPress/WooCommerce extraction, we need clean module boundaries. The current single-module Maven project with classpath-based discovery works but creates tight coupling between IR core and web crawling concepts.

---

## 1. Package Inventory and Classification

### Production packages (16 packages, 65 files)

| Package | Files | Classification | Key types |
|---------|:---:|---|---|
| `codex.ir` | 1 | **Core** | `Document` record |
| `codex.ir.tokenizer` | 2 | **Core** | `Tokenizer`, `Tokenizers` |
| `codex.ir.normalizer` | 2 | **Core** | `Normalizer`, `Normalizers` |
| `codex.ir.concurrent` | 4 | **Core** | `Debouncer`, `VTExecutor`, `VTConfig` |
| `codex.ir.corpus` | 3 | **Core** | `Corpus`, `Corpora`, `CorpusStatistics` |
| `codex.ir.corpus.vector` | 2 | **Core** | `Vocabulary`, `Vocabularies` |
| `codex.ir.indexer` | 5 | **Core** | `Indexer`, `Indexers`, `InvertedIndex`, `InvertedIndexes`, `Posting` |
| `codex.ir.ranking` | 2 | **Core** | `Ranker`, `Rankers` |
| `codex.ir.search` | 5 | **Core** | `Searcher`, `Searchers`, `SimpleSearcher`, `VectorSearcher`, `SearchResult` |
| `codex.ir.vector` | 7 | **Core** | `Vectorizer`, `Vectorizers`, `Similarity`, `Similarities`, `SparseDocumentVector` |
| `codex.ir.vector.store` | 2 | **Core** | `DocumentVectorStore`, `VectorStores` |
| `codex.ir.weight` | 2 | **Core** | `DocumentWeighter`, `Weighters` |
| `codex.ir.util` | 3 | **Split** | `TermWeightingUtils` (core), `HttpUtil` (web), `UriUtil` (web) |
| `codex.ir.canonicalizer` | 2 | **Web** | `UriCanonicalizer`, `UriCanonicalizers` |
| `codex.ir.ingestion` | 8 | **Web** | `DocumentSource`, `DocumentMapper`, `Ingestors`, `Mappers`, `Sources`, `WebPage`, `WebCrawlingConfig`, `DocumentIngestionService` |
| `codex.ir.ingestion.crawler` | 10 | **Web** | `SiteTraversalStrategy`, `JsoupWebPageFetcher`, `VisitedUriRegistry`, `WebPageSourceStrategies` |
| `codex.ir.ingestion.crawler.fetcher` | 3 | **Web** | `WebHttpFetcher`, `WebHttpFetchers`, `WebHttpResponse` |

### Application entry point

| File | Classification |
|------|---------------|
| `codex.Main` | **App/Demo** |

### Test packages (10 packages, 15 test files)

| Test package | Tests | Destination |
|---|---|---|
| `codex.ir.tokenizer` | 1 | Core |
| `codex.ir.normalizer` | 1 | Core |
| `codex.ir.concurrent` | 1 | Core |
| `codex.ir.corpus` | 1 | Core |
| `codex.ir.corpus.vector` | 1 | Core |
| `codex.ir.indexer` | 3 | Core |
| `codex.ir.ranking` | 1 | Core |
| `codex.ir.search` | 2 | Core |
| `codex.ir.vector` | 2 | Core |
| `codex.ir.weight` | 1 | Core |
| `codex.ir.canonicalizer` | 1 | **Web** (moves with canonicalizer package) |

---

## 2. Dependency Graph Analysis

The full cross-package dependency matrix was analyzed. Key findings:

### Leaf packages (no internal deps)

`tokenizer`, `normalizer`, `concurrent`, `canonicalizer`, `corpus.vector`, `Document`

### Two circular dependencies

| # | Cycle | Resolution |
|---|-------|------------|
| 1 | `indexer` ↔ `weight` | Must stay in same module (`Indexers` needs `DocumentWeighter`; `Weighters` needs `InvertedIndex`) |
| 2 | `ingestion` ↔ `ingestion.crawler` | Same package tree — both stay in web module |

### Cross-boundary web → core dependencies

The web/ingestion subsystem depends on core via exactly two types:

```
Mappers.webPage()    → codex.ir.Document
Ingestors.simple()   → codex.ir.indexer.Indexer
```

The crawler subpackage (`ingestion.crawler`) has **zero** dependencies on core IR. It only depends on `ingestion` (parent), `canonicalizer`, `concurrent`, `util`, and `fetcher`. This is a clean boundary.

### Utility package split required

| Utility class | Used by | Destination |
|---|---|---|
| `TermWeightingUtils` | `ranking` (core), `weight` (core) | Core |
| `HttpUtil` | `ingestion.crawler` (web) | Web |
| `UriUtil` | `ingestion.crawler` (web), depends on `WebCrawlingConfig` (web) | Web |

---

## 3. Proposed Maven Module Structure

```
myIR/
├── pom.xml                          ← parent (packaging: pom)
├── codex-ir-core/
│   ├── pom.xml                      ← depends on: nothing (except slf4j)
│   └── src/
│       ├── main/java/
│       │   ├── module-info.java     ← module codex.ir.core
│       │   └── codex/ir/
│       │       ├── Document.java
│       │       ├── tokenizer/
│       │       ├── normalizer/
│       │       ├── concurrent/
│       │       ├── corpus/
│       │       ├── indexer/
│       │       ├── ranking/
│       │       ├── search/
│       │       ├── vector/
│       │       ├── weight/
│       │       └── util/
│       │           └── TermWeightingUtils.java
│       └── test/java/codex/ir/
├── codex-ir-web/
│   ├── pom.xml                      ← depends on: codex-ir-core
│   └── src/
│       ├── main/java/
│       │   ├── module-info.java     ← module codex.ir.web
│       │   └── codex/ir/
│       │       ├── canonicalizer/
│       │       ├── ingestion/
│       │       └── util/
│       │           ├── HttpUtil.java
│       │           └── UriUtil.java
│       └── test/java/codex/ir/
│           └── canonicalizer/
└── codex-ir-app/
    ├── pom.xml                      ← depends on: codex-ir-core, codex-ir-web
    └── src/
        └── main/java/
            ├── module-info.java     ← module codex.ir.app
            └── codex/
                └── Main.java
```

### Dependency direction

```
codex-ir-app ──→ codex-ir-core
     │
     └──────────→ codex-ir-web ──→ codex-ir-core
```

Core has zero dependencies on web. Web depends on core for `Document` and `Indexer`. App depends on both. Future `codex-ir-woocommerce` would depend on `codex-ir-web`.

---

## 4. Proposed Java Module Names

| Module | JPMS name | Exports |
|--------|-----------|---------|
| Core | `codex.ir.core` | `codex.ir`, `codex.ir.corpus`, `codex.ir.corpus.vector`, `codex.ir.indexer`, `codex.ir.ranking`, `codex.ir.search`, `codex.ir.tokenizer`, `codex.ir.normalizer`, `codex.ir.vector`, `codex.ir.vector.store`, `codex.ir.weight`, `codex.ir.concurrent` |
| Web | `codex.ir.web` | `codex.ir.ingestion`, `codex.ir.ingestion.crawler`, `codex.ir.canonicalizer` |
| App | `codex.ir.app` | *(none — entry point only)* |

### module-info.java sketches

**codex-ir-core:**
```java
module codex.ir.core {
    requires org.slf4j;
    exports codex.ir;
    exports codex.ir.corpus;
    exports codex.ir.corpus.vector;
    exports codex.ir.indexer;
    exports codex.ir.ranking;
    exports codex.ir.search;
    exports codex.ir.tokenizer;
    exports codex.ir.normalizer;
    exports codex.ir.vector;
    exports codex.ir.vector.store;
    exports codex.ir.weight;
    exports codex.ir.concurrent;
}
```

**codex-ir-web:**
```java
module codex.ir.web {
    requires codex.ir.core;
    requires org.jsoup;
    requires java.net.http;
    exports codex.ir.ingestion;
    exports codex.ir.ingestion.crawler;
    exports codex.ir.canonicalizer;
}
```

**codex-ir-app:**
```java
module codex.ir.app {
    requires codex.ir.core;
    requires codex.ir.web;
}
```

---

## 5. Package Move Summary

### Maintained in `codex-ir-core`

| Package | Reason |
|---------|--------|
| `codex.ir` | Foundation — used by core and web |
| `codex.ir.tokenizer` | Leaf — no internal deps |
| `codex.ir.normalizer` | Leaf — no internal deps |
| `codex.ir.concurrent` | Used by `corpus` (core) and `crawler` (web); keep in core since web already `requires` core |
| `codex.ir.corpus` | Core concept |
| `codex.ir.corpus.vector` | Core concept |
| `codex.ir.indexer` | Core concept; cycle with `weight` means both must stay together |
| `codex.ir.ranking` | Core concept |
| `codex.ir.search` | Core concept |
| `codex.ir.vector` | Core concept |
| `codex.ir.vector.store` | Core concept |
| `codex.ir.weight` | Core concept; cycle with `indexer` means both must stay together |
| `codex.ir.util.TermWeightingUtils` | Used by `ranking` and `weight` (both core) |

### Moved to `codex-ir-web`

| Package / Class | Reason |
|-----------------|--------|
| `codex.ir.canonicalizer` | Only used by web crawler |
| `codex.ir.ingestion` | Web ingestion API |
| `codex.ir.ingestion.crawler` | Web crawler mechanics |
| `codex.ir.ingestion.crawler.fetcher` | Web HTTP transport |
| `codex.ir.util.HttpUtil` | Only used by web crawler |
| `codex.ir.util.UriUtil` | Only used by web crawler; depends on `WebCrawlingConfig` |

### Moved to `codex-ir-app`

| File | Reason |
|------|--------|
| `codex.Main` | Application entry point; depends on everything |

### Resources

| Resource | Destination |
|----------|------------|
| `stopwords_en.txt`, `stopwords_es.txt` | `codex-ir-core/src/main/resources/` |
| `logback.xml` | `codex-ir-app/src/main/resources/` |

### Tests

| Test file | Destination |
|-----------|------------|
| `UriCanonicalizersTest.java` | Moves to `codex-ir-web/src/test/` |
| All other 14 test files | Stay in `codex-ir-core/src/test/` |

---

## 6. Risks and Mitigations

| Risk | Severity | Mitigation |
|------|----------|------------|
| `concurrent` needed by both modules | Low | Keep in core; web already `requires core` for `Document` and `Indexer` |
| `package-private` access breakage | Low | Factory pattern already hides implementations behind interfaces; no private access crosses proposed module boundaries |
| Missing `exports` causing compile errors | Moderate | Start with minimal exports; add iteratively as compiler reveals missing ones |
| JUnit on module path | Moderate | Keep tests on classpath initially; add `--add-opens` for reflection if needed |
| Resource loading (`stopwords`) | Low | Resources stay in core module; `getResourceAsStream` works intra-module |
| `jsoup`/`playwright` in core | Low | Move to `codex-ir-web/pom.xml` only; core doesn't use them |
| `Main.java` requires all factories | Low | Already uses factories exclusively; `requires codex.ir.core` + `requires codex.ir.web` suffices |

---

## 7. Incremental Execution Plan (5 phases)

### Phase 1 — Parent + core module (no moves)

```
1. Create parent pom.xml at root (packaging: pom, <modules> listing all 3)
2. Rename existing pom.xml → codex-ir-core/pom.xml
3. Move src/ → codex-ir-core/src/ (git mv)
4. Adjust parent reference in codex-ir-core/pom.xml
5. Remove jsoup + playwright from core POM (web-only deps)
6. mvn compile && mvn test from root → 155 tests pass
```

**Goal:** Identical behavior, different file layout. Zero code changes.

### Phase 2 — Create empty web module

```
1. Create codex-ir-web/pom.xml (depends on codex-ir-core)
2. Create codex-ir-web/src/main/java/module-info.java (empty exports)
3. mvn compile from root → succeeds
```

### Phase 3 — Move web packages

```
1. git mv codex/ir/canonicalizer → codex-ir-web/src/main/java/
2. git mv codex/ir/ingestion/    → codex-ir-web/src/main/java/
3. git mv codex/ir/util/HttpUtil.java  → codex-ir-web/
4. git mv codex/ir/util/UriUtil.java   → codex-ir-web/
5. git mv UriCanonicalizersTest.java   → codex-ir-web/src/test/
6. Add exports to module-info.java
7. mvn compile → fix any missing exports
8. mvn test → 155 tests pass (1 test moved, all pass)
```

### Phase 4 — Create app module

```
1. Create codex-ir-app/pom.xml (depends on core + web)
2. git mv codex/Main.java → codex-ir-app/src/main/java/
3. Add module-info.java with requires directives
4. mvn compile from root
```

### Phase 5 — Stabilize

```
1. mvn clean compile test from root → 155 tests pass
2. Verify no split packages: jdeps -summary each module
3. Verify module graph: jdeps -dotoutput each module
4. Commit
```

---

## 8. Recommendation for Future SitemapSiteTraversalStrategy

After modularization, the new sitemap strategy belongs in:

```
codex-ir-web/src/main/java/codex/ir/ingestion/crawler/sitemap/
```

**Rationale:**
- Depends on `WebPageFetcher`, `UriCanonicalizer`, `WebCrawlingConfig` — all in web module
- Depends on `DocumentSource<WebPage>` — in `ingestion` package (web module)
- Fills the existing `SiteMapStrategy` stub in `WebPageSourceStrategies.java` (web module)
- No core IR dependencies
- No core changes needed

---

## 9. Do NOT Do

| Do NOT | Reason |
|--------|--------|
| Create `codex-ir-woocommerce` now | Future task; web module is sufficient foundation |
| Implement sitemap traversal now | Modularization comes first |
| Split core into sub-modules | `indexer` ↔ `weight` cycle prevents it |
| Use `opens` for reflection | Codebase uses no reflection/dynamic proxies |
| Force module-path for tests in phase 1 | Classpath is sufficient during migration |
| Move `concurrent` out of core | Used by both sides; core is natural home |

---

This plan is intentionally a design document. No code changes.
