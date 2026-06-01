# AGENTS.md

This file provides guidance for AI coding agents working in this repository.

## Build & Test

- **Compile:** `mvn compile`
- **All tests:** `mvn test`
- **Single test in a module:** `mvn test -pl codex-ir-core -Dtest=codex.ir.ranking.RankersTest` (use `-pl <module>` to avoid scanning all modules; fully-qualified class name avoids ambiguity)
- **Full verification:** `mvn compile && mvn test-compile && mvn test`
- **Requires Java 25.** Maven is pinned to source=25 target=25 in `pom.xml`. No other JDK version will work.
- **Playwright prerequisite:** `npx playwright install` must be run once before any crawler demo or test that touches `WebPageFetcher`. Without browser binaries, the Playwright dependency will fail at runtime.

## Architecture

### Module layout

Multi-module Maven project (`pom.xml` at root). Three modules:

| Maven module | JPMS module | Responsibility |
|---|---|---|
| `codex-ir-core` | `codex.ir.core` | Core IR engine — indexing, search, ranking, vectors |
| `codex-ir-web` | `codex.ir.web` | Web crawling, ingestion, canonicalization, product extraction |
| `codex-ir-app` | `codex.ir.app` | Entry point only (`codex/Main.java`); depends on both core and web |

Each module has the standard Maven layout (`src/main/java`, `src/test/java`). Tests mirror the source package structure.

#### `codex-ir-core` packages (`codex.ir.*`)

| Package | Responsibility |
|---|---|
| `corpus/` | `Corpus` + `CorpusStatistics`; aggregate stats debounced via `Debouncer` |
| `corpus/vector/` | `Vocabulary` — maps terms to stable integer dimension IDs |
| `indexer/` | `InvertedIndex`, `Indexer` pipeline (lexical + vector stages inside `Indexers`) |
| `normalizer/` | Stop-word removal, stemming; reads `stopwords_en/es.txt` from resources |
| `tokenizer/` | Whitespace/regex splitting |
| `ranking/` | `Ranker` implementations: binary, TF-IDF, BM25 |
| `search/` | `SimpleSearcher` (lexical) and `VectorSearcher`; assembled via `Searchers` |
| `vector/` | Sparse vector types (`SparseDocumentVector`, `SimilarityResult`), `Vectorizer`, `Similarity` |
| `vector/store/` | `DocumentVectorStore` / `VectorStores` — in-memory sparse vector persistence |
| `weight/` | `DocumentWeighter` / `Weighters` — computes per-term weights (TF-IDF) before vectorizing |
| `concurrent/` | `Debouncer`, `VTExecutor` / `VTExecutors`, `VTConfig` — virtual-thread helpers |
| `util/` | `TermWeightingUtils` — stateless helper. **Not JPMS-exported** (internal to core; inaccessible from `codex.ir.app` and `codex.ir.web`) |

#### `codex-ir-web` packages (`codex.ir.*`)

Exported packages (accessible to `codex.ir.app` and other dependents):

| Package | Responsibility |
|---|---|
| `ingestion/` | `DocumentSource`, `DocumentMapper`, `DocumentIngestionService`, `Ingestors` |
| `ingestion/crawler/` | Public crawler API: `WebPageFetcher`, `WebPageSourceStrategy`, fetcher registries, `WebCrawlerRuntime` |
| `ingestion/crawler/classifier/` | `UrlClassifier`, `PageClassifier` — classify URLs and pages; produces `ClassifiedUrl`, `PageClassification`, `UrlType` |
| `ingestion/crawler/filter/` | `UrlFilter` / `UrlFilters` — compose URL filtering predicates |
| `ingestion/crawler/metadata/` | `PageMetadataExtractor` / `PageMetadataExtractors` — extracts `PageMetadata` (title, OG tags, headings, JSON-LD) from a `WebPage` |
| `ingestion/crawler/product/` | `ProductDiscoverer` / `ProductDiscoverers`, `ProductDetailExtractor`, `ProductCardExtractor` and their result types (`ProductCard`, `ProductDetail`, `ProductDiscoveryResult`, `ProductPrice`, `ProductImage`) |
| `canonicalizer/` | URI normalization pipeline; `UriCanonicalizer` / `UriCanonicalizers` |

Non-exported packages (used only within `codex.ir.web`):

| Package | Responsibility |
|---|---|
| `ingestion/crawler/fetcher/` | `WebHttpFetcher` / `WebHttpFetchers` — lightweight HTTP-only fetcher; `WebHttpResponse` |
| `ingestion/crawler/internal/classifier/` | `JsoupGenericPageClassifier`, `WordPressWooCommercePageClassifier`, `HtmlSignals` — backing implementations for `PageClassifiers` |
| `ingestion/crawler/internal/sitemap/` | `SitemapParser`, `RobotsParser`, `SitemapSiteTraversalStrategy` — sitemap-driven traversal |
| `ingestion/crawler/internal/traversal/` | `SiteTraversalStrategy`, `SeededWebPageTraversal` — traversal abstractions |
| `ingestion/crawler/internal/product/` | `JsonLdProductExtractor`, `ProductPriceParser`, image resolvers — internal extraction logic |
| `ingestion/crawler/internal/metadata/` | `DocumentMetadataContributor`, `HeadingExtractor`, `MetaTagExtractor`, `OpenGraphExtractor`, `TwitterCardExtractor`, `RobotsMetaExtractor`, `JsonLdBlockExtractor` — contributors wired by `PageMetadataExtractors` |
| `web/util/` | `HttpUtil`, `UriUtil` — stateless HTTP/URI helpers |

### JPMS module graph

`codex.ir.app` → `codex.ir.web` → `codex.ir.core`. The `codex.ir.web` module exports seven packages (see "Exported packages" above). Internal packages — `crawler/internal/`, `crawler/fetcher/`, and `web/util/` — are inaccessible to `codex.ir.app` regardless of their directory location.

### Intended module direction

The codebase is moving toward a cleaner four-area split. New work should respect these boundaries even while everything lives inside the current modules:

| Intended future module | Current location | Responsibility |
|---|---|---|
| `codex.ir.core` | `codex-ir-core` | Tokenization, indexing, ranking, corpus, search |
| `codex.ir.ingestion.web` | `codex-ir-web` — crawler/ingestion packages | Fetch, URL classification, crawling, WebPage, page metadata |
| `codex.ir.ingestion.extraction` | `codex-ir-web` — product packages | ProductDiscoverer, JSON-LD, OpenGraph, HTML product heuristics |
| `codex.ir.export` | _(not yet created)_ | CSV/JSON export, dotCMS import candidates |

### Interface + Factory pattern

Every domain concept follows: `Xxx` interface + `Xxxes` static factory class. Implementations are hidden `static` inner classes of the factory (some `private`, some package-private, often `final`). The concrete types must never leak into public APIs — callers use the interface type exclusively.

Complete list — **core:** `Corpus`/`Corpora`, `Tokenizer`/`Tokenizers`, `Normalizer`/`Normalizers`, `Ranker`/`Rankers`, `Indexer`/`Indexers`, `Searcher`/`Searchers`, `Vectorizer`/`Vectorizers`, `Vocabulary`/`Vocabularies`, `Similarity`/`Similarities`, `DocumentVectorStore`/`VectorStores`, `DocumentWeighter`/`Weighters`, `VTExecutor`/`VTExecutors`, `InvertedIndex`/`InvertedIndexes`. **web:** `UriCanonicalizer`/`UriCanonicalizers`, `WebHttpFetcher`/`WebHttpFetchers`, `WebPageFetcher`/`WebPageFetchers`, `WebPageSourceStrategy`/`WebPageSourceStrategies`, `VisitedUriRegistry`/`VisitedUriRegistries`, `DocumentSource`/`Sources`, `DocumentMapper`/`Mappers`, `UrlClassifier`/`UrlClassifiers`, `UrlFilter`/`UrlFilters`, `PageClassifier`/`PageClassifiers`, `PageMetadataExtractor`/`PageMetadataExtractors`, `ProductCardExtractor`/`ProductCardExtractors`, `ProductDetailExtractor`/`ProductDetailExtractors`, `ProductDiscoverer`/`ProductDiscoverers`.

### Domain types

Core data types use Java **records**: `Document`, `Posting`, `SearchResult`, `SparseDocumentVector`, `CorpusStatistics`, `WebPage`, `WebCrawlingConfig`, `VTConfig`, `WebHttpResponse`, `SimilarityResult`, `SimilarityMatch`, `SparseVectorMetadata`, `PageMetadata`, `ClassifiedUrl`, `PageClassification`, `ProductCard`, `ProductDetail`, `ProductDiscoveryResult`, `ProductPrice`, `ProductImage`.

### Document and fields

`Document` carries `rawContent`, `normalizedContent`, `fields` (`Map<String,String>`), and `DocumentMetadata` (term frequencies, length, uniqueTerms, extensible `attributes`). When `fields` is non-empty the `DocumentPreprocessor` (inside `Indexers`) **aggregates field values** as the content to normalize instead of `rawContent`. If all field values are blank it falls back to `rawContent`.

### Indexing pipeline

`Indexers` assembles a `PipelineIndexer`: one shared `DocumentPreprocessor` runs first, then `LexicalIndexer` and/or `VectorIndexer` run in sequence on the preprocessed document. Public entry points: `Indexers.lexical(...)`, `Indexers.vector(...)`, `Indexers.lexicalAndVector(...)`.

### Search flows

**Lexical:** query → tokenize/normalize → `InvertedIndex` lookup → `Ranker` (binary / TF-IDF / BM25) → `SearchResult` list.

**Vector:** query → tokenize/normalize → term weights → sparse query vector → cosine similarity against `DocumentVectorStore` → `SimilarityResult` with matched-term explanations.

### In-memory only (by design)

All core structures (corpus, inverted index, vector store, vocabulary) are in-memory. This is deliberate — see `docs/ADR-003.md`. Do not introduce persistence without explicit request.

## ADRs

Architectural decisions are captured in `docs/ADR-NNN.md`. Read the relevant ADR before changing the subsystem it covers. Also see `docs/CODING_IDENTITY.md` for design philosophy.

## Coding conventions

- **Composition over inheritance.** Build small collaborating parts. Avoid deep type trees.
- **Records over raw maps.** Wrap domain data in records; avoid passing around bare `Map<String,Object>`.
- **Primitives first.** Use `int`, `long`, `boolean` unless a wrapper type is required.
- **Public API clarity.** Public methods should be ≤20 lines, documented with JavaDoc, and validate nulls with `Objects.requireNonNull`.
- **No hidden magic.** Avoid dynamic proxies, reflection, bytecode manipulation, or annotation-driven behavior unless explicitly requested.
- **Do NOT modify `pom.xml` or introduce new dependencies** unless the task explicitly calls for it.
- **If a task is documentation-only, make ZERO code changes.**

## Record + Builder pattern

When a record needs to be constructed incrementally or copied with modifications, add a `Builder` inner class and factory methods on the record itself. Follow this shape:

```java
public record Foo(Optional<String> name, List<String> tags, Map<String, String> attrs) {

    public Foo {
        Objects.requireNonNull(name);
        Objects.requireNonNull(tags);
        Objects.requireNonNull(attrs);
        tags = List.copyOf(tags);      // defensive copy for mutable inputs
        attrs = Map.copyOf(attrs);
    }

    public static Foo empty() { return new Foo(Optional.empty(), List.of(), Map.of()); }
    public static Builder builder() { return new Builder(); }

    public Builder toBuilder() {
        Builder b = new Builder();
        name.ifPresent(b::name);       // use ifPresent, not orElse(null), to avoid null setter
        b.tags(tags);
        b.attrs(attrs);
        return b;
    }

    public static final class Builder {
        private String name;           // null = absent for Optional fields
        private List<String> tags = List.of();
        private Map<String, String> attrs = Map.of();

        private Builder() {}

        public Builder name(String name)   { this.name = Objects.requireNonNull(name); return this; }
        public Builder tags(List<String> tags) { this.tags = Objects.requireNonNull(tags); return this; }
        public Builder attrs(Map<String, String> attrs) { this.attrs = Objects.requireNonNull(attrs); return this; }

        public Foo build() {
            return new Foo(Optional.ofNullable(name), tags, attrs);
        }
    }
}
```

Key rules:
- **Compact constructor** validates all fields with `Objects.requireNonNull` and defensively copies `List`/`Map` fields with `List.copyOf`/`Map.copyOf`.
- **Optional fields use `String` in the builder** (null = absent); `build()` wraps with `Optional.ofNullable`. Never store `null` Optional in the record.
- **`toBuilder()` uses `ifPresent`** to copy Optional fields — never passes `null` to a setter.
- **`empty()`** is a static factory returning the all-absent/empty-collection instance.
- Records already provide `equals`, `hashCode`, `toString`, and component accessors — do not add them.

## Concurrency

- Prefer **Virtual Threads** and **Structured Concurrency** over platform threads or executor pools.
- Use `ScopedValue` over `ThreadLocal`.
- When iterating over a `synchronized` list in tests, synchronize **explicitly** on the list object during the loop.

## Test conventions

- JUnit 5 (`junit-jupiter`), no Mockito, no AssertJ.
- No shared base classes or fixtures — each test is self-contained.
- Tests mirror the source package structure (e.g. `src/test/java/codex/ir/ranking/RankersTest.java`).
- Tests are named descriptively (e.g. `bm25RankerShouldPenalizeLongerDocumentWhenTermFrequencyMatches`).
- Concurrency tests use `CountDownLatch`, `CopyOnWriteArrayList`, `AtomicInteger` manually.
- HTML fixture files for web tests live in `codex-ir-web/src/test/resources/fixtures/`.

## Resources

- `codex-ir-core/src/main/resources/stopwords_en.txt`, `stopwords_es.txt` — used by `Normalizers`
- `codex-ir-core/src/main/resources/logback.xml` — console logging at INFO level
- `Normalizers.StopWordNormalizer` resolves stop-word files via the `IR_STOPWORDS_PATH` env var, then a `classpath:` or `file:` prefix, falling back to `/stopwords.txt`. The `classpath:` prefix is a project-specific convention; standard resource paths without the prefix also work.

## Reporting

After each task, report: files changed, tests run, and any architectural questions discovered.
