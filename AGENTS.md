# AGENTS.md

## Build & Test

- **Compile:** `mvn compile`
- **All tests:** `mvn test`
- **Single test in a module:** `mvn test -pl codex-ir-core -Dtest=codex.ir.ranking.RankersTest` (use `-pl <module>` to avoid scanning all modules; fully-qualified class name avoids ambiguity)
- **Full verification:** `mvn compile && mvn test-compile && mvn test`
- **Requires Java 25.** Maven is pinned to source=25 target=25 in `pom.xml`. No other JDK version will work.
- **Playwright prerequisite:** `npx playwright install` must be run once before any crawler test that touches `WebPageFetcher`. Without browser binaries, the Playwright dependency will fail at runtime.

## Module layout

Three Maven modules, each with its own `module-info.java` (JPMS):

| Maven module | JPMS module | Responsibility |
|---|---|---|
| `codex-ir-core` | `codex.ir.core` | Core IR engine — indexing, search, ranking, vectors |
| `codex-ir-web` | `codex.ir.web` | Web crawling, ingestion, canonicalization, product extraction |
| `codex-ir-app` | `codex.ir.app` | Entry point (`codex/Main.java`) |

Dependency direction: `app → web → core`. The `module-info.java` files are the authoritative source for exported vs. internal packages. Internal packages under `codex.ir.web` (`crawler/internal/`, `crawler/fetcher/`, `web/util/`) are inaccessible to `app`.

**Package quick reference — `codex-ir-core`:** `corpus/`, `indexer/`, `normalizer/`, `tokenizer/`, `ranking/`, `search/`, `vector/` (+ `vector/store/`), `weight/`, `concurrent/`, `util/` (internal — not JPMS-exported).

**Package quick reference — `codex-ir-web`:**
- **Exported:** `canonicalizer/`, `ingestion/`, `ingestion/crawler/`, `ingestion/crawler/classifier/`, `ingestion/crawler/filter/`, `ingestion/crawler/metadata/`, `ingestion/crawler/product/`
- **Internal (not exported):** `ingestion/crawler/fetcher/`, `ingestion/crawler/internal/*` (classifier/, sitemap/, traversal/, product/, metadata/, text/), `web/util/`

## Architecture

- **Interface + Factory pattern.** Every domain concept: `Xxx` interface + `Xxxes` static factory class. Implementations are `private`/package-private inner classes of the factory. Callers use the interface type exclusively. Core examples: `Corpus`/`Corpora`, `Indexer`/`Indexers`, `Ranker`/`Rankers`, `Searcher`/`Searchers`, `Normalizer`/`Normalizers`, `Tokenizer`/`Tokenizers`. Web examples: `UriCanonicalizer`/`UriCanonicalizers`, `PageClassifier`/`PageClassifiers`, `ProductDiscoverer`/`ProductDiscoverers`. All follow this same shape.
- **Domain types are Java records.** `Document`, `Posting`, `SearchResult`, `SparseDocumentVector`, `WebPage`, `PageMetadata`, etc. No bare `Map<String,Object>`.
- **Document field aggregation.** When `Document.fields` is non-empty, the `DocumentPreprocessor` aggregates field values as content to normalize instead of `rawContent`. Falls back to `rawContent` if all field values are blank.
- **In-memory only (by design).** All core structures (corpus, inverted index, vector store, vocabulary) live in memory. See `docs/ADR-003.md`. Do not introduce persistence without explicit request.
- **ADRs.** Architectural decisions in `docs/ADR-NNN.md`. Read the relevant ADR before changing the subsystem it covers. Also see `docs/CODING_IDENTITY.md` for design philosophy.

## Coding conventions

- **No hidden magic.** No reflection, dynamic proxies, bytecode manipulation, or annotation-driven behavior.
- **No Mockito, no AssertJ.** JUnit 5 (`junit-jupiter`) only. Tests are self-contained with no shared base classes.
- **Do NOT modify `pom.xml` or introduce new dependencies** unless the task explicitly calls for it.
- **If a task is documentation-only, make ZERO code changes.**
- Public methods should be ≤20 lines, documented with JavaDoc, and validate nulls with `Objects.requireNonNull`.
- Tests mirror source package structure and use descriptive names (e.g. `bm25RankerShouldPenalizeLongerDocumentWhenTermFrequencyMatches`).
- Concurrency tests use `CountDownLatch`, `CopyOnWriteArrayList`, `AtomicInteger` manually.
- Concurrency: prefer Virtual Threads and Structured Concurrency; use `ScopedValue` over `ThreadLocal`.

## Record + Builder pattern

When a record needs incremental construction or copy-with-modifications, add a `Builder` inner class. Key rules:
- Compact constructor: `Objects.requireNonNull` on all params, `List.copyOf`/`Map.copyOf` for mutable fields
- `Optional` fields use raw type in builder (null = absent); `build()` wraps with `Optional.ofNullable`
- `toBuilder()` uses `ifPresent` to copy Optional fields
- `empty()` static factory returns the all-absent/empty-collection instance
- Do not add `equals`/`hashCode`/`toString`/accessors — records provide them

## Architecture Discipline

Application-specific code must not leak into reusable modules:
- `codex-ir-core` must remain domain-agnostic (no web concepts).
- `codex-ir-web` may contain reusable crawling, fetching, robots, sitemap, URI, and web traversal primitives.
- Concrete applications (SYJ scraping, site exporters) live under `codex-ir-app`.
- New dependencies such as PDF or ePub renderers must be placed behind ports/interfaces.

## Resources

- `codex-ir-core/src/main/resources/stopwords_en.txt`, `stopwords_es.txt` — used by `Normalizers`
- `codex-ir-core/src/main/resources/logback.xml` — console logging at INFO level
- `Normalizers.StopWordNormalizer` resolves stop-word files via `IR_STOPWORDS_PATH` env var, then `classpath:` or `file:` prefix, falling back to `/stopwords.txt`
- HTML fixture files for web tests: `codex-ir-web/src/test/resources/fixtures/`
- Every package has a `package-info.java` describing its purpose — read before adding types to a new package.
