# AGENTS.md

## Build & Test

- **Compile:** `mvn compile`
- **All tests:** `mvn test`
- **Single test in a module:** `mvn test -pl codex-ir-core -Dtest=codex.ir.ranking.RankersTest` (use `-pl <module>` to avoid scanning all modules; fully-qualified class name avoids ambiguity)
- **Full verification:** `mvn compile && mvn test-compile && mvn test`
- **Requires Java 25.** Maven is pinned to `source=25 target=25` in `pom.xml`. No other JDK version works.
- **Playwright prerequisite:** `npx playwright install` must be run once before any test touching `WebPageFetcher`. Without browser binaries, Playwright-dependent tests fail at runtime.
- **No Maven wrapper.** Install Maven directly (any recent version).

## Running the application

| Entry point | Command |
|---|---|
| `codex.scraper.Main` (in-memory indexing + search demos) | `mvn exec:java -pl codex-ir-app -Dexec.mainClass="codex.scraper.Main"` |
| `codex.scraper.DiscoveryRunner` (product discovery) | `mvn exec:java -pl codex-ir-app -Dexec.mainClass="codex.scraper.DiscoveryRunner"` |
| `codex.scraper.QuickDiscoveryRunner` (IDE wrapper with embedded args) | `mvn exec:java -pl codex-ir-app -Dexec.mainClass="codex.scraper.QuickDiscoveryRunner"` |
| `codex.apps.siteexporter.SiteExporterCommand` | `mvn exec:java -pl codex-ir-app -Dexec.mainClass="codex.apps.siteexporter.SiteExporterCommand" -Dexec.args="--url https://example.com/"` |

For full SiteExporter parameter reference, see `README.md`.

## Module layout

Three Maven modules, each with its own `module-info.java` (JPMS):

| Maven module | JPMS module | Responsibility |
|---|---|---|
| `codex-ir-core` | `codex.ir.core` | Core IR engine — indexing, search, ranking, vectors |
| `codex-ir-web` | `codex.ir.web` | Web crawling, ingestion, canonicalization, product extraction |
| `codex-ir-app` | `codex.ir.app` | Application entry points (`codex.scraper.Main`, `codex.apps.siteexporter.SiteExporterCommand`) |

Dependency direction: `app → web → core`. The `module-info.java` files are the authoritative source for exported vs. internal packages. In `codex.ir.core`, `codex.ir.util` is not exported. In `codex.ir.web`, internal packages (`ingestion/crawler/fetcher/`, `ingestion/crawler/internal/*`, `web/util/`) are inaccessible to `app`. Every other `codex.ir.*` package under core and web is exported.

### JPMS quirk for Jackson

`codex-ir-app/src/main/java/module-info.java` opens `codex.apps.siteexporter` to `com.fasterxml.jackson.databind` so Jackson can access private Builder constructors via reflection. If you add a new Jackson-involved record+Builder to the site exporter, update the `opens` directive.

## Architecture

- **Interface + Factory pattern.** Every domain concept: `Xxx` interface + `Xxxes` static factory class. Implementations are package-private inner classes of the factory. Callers use the interface type exclusively. Core examples: `Corpus`/`Corpora`, `Indexer`/`Indexers`, `Ranker`/`Rankers`, `Searcher`/`Searchers`, `Normalizer`/`Normalizers`, `Tokenizer`/`Tokenizers`.
- **Domain types are Java records.** `Document`, `Posting`, `SearchResult`, `SparseDocumentVector`, `WebPage`, `PageMetadata`, etc. No bare `Map<String,Object>`.
- **In-memory only (by design).** All core structures (corpus, inverted index, vector store, vocabulary) live in memory. See `docs/ADR-003.md`. Do not introduce persistence without explicit request.
- **Document field aggregation.** When `Document.fields` is non-empty, the `DocumentPreprocessor` aggregates field values as content instead of using `rawContent`. Falls back to `rawContent` if all field values are blank. Field names are discarded after preprocessing — the current model is whole-document only (see `docs/adrs/ADR-004.md`).
- **ADRs.** Architectural decisions in `docs/adrs/`. Read the relevant ADR before changing the subsystem it covers.
- **Design philosophy.** See `docs/CODING_IDENTITY.md`.

## Coding conventions

- **No hidden magic.** No reflection, dynamic proxies, bytecode manipulation, or annotation-driven behavior.
- **No Mockito, no AssertJ.** JUnit 5 (`junit-jupiter`) only. Tests are self-contained with no shared base classes.
- **Do NOT modify `pom.xml` or introduce new dependencies** unless the task explicitly calls for it.
- **If a task is documentation-only, make ZERO code changes.**
- Public methods should be ≤20 lines, documented with JavaDoc, and validate nulls with `Objects.requireNonNull`.
- Tests mirror source package structure and use descriptive names (e.g. `bm25RankerShouldPenalizeLongerDocumentWhenTermFrequencyMatches`).
- Concurrency tests use `CountDownLatch`, `CopyOnWriteArrayList`, `AtomicInteger` manually — no concurrency frameworks.
- Concurrency: prefer Virtual Threads and Structured Concurrency; use `ScopedValue` over `ThreadLocal`.

## Record + Builder pattern

When a record needs incremental construction, add a `Builder` inner class. Key rules:
- Compact constructor: null-check reference params with `Objects.requireNonNull`; use `List.copyOf`/`Map.copyOf` for mutable collection fields.
- Builder fields for nullable record values use the raw type (e.g. `Integer length`, not `Optional<Integer>`); `null` signals absent.
- If a copy-constructor pattern is needed, accept the source record in the Builder constructor (see `Document.Builder(Document)`).
- Factory `empty()` returns an instance with all-absent/null fields (see `DocumentMetadata.empty()`, `CorpusStatistics.empty()`).
- Do not add `equals`/`hashCode`/`toString`/accessors — records provide them.

## Architecture Discipline

- `codex-ir-core` must remain domain-agnostic (no web concepts).
- `codex-ir-web` may contain reusable crawling, fetching, robots, sitemap, URI, and web traversal primitives.
- Concrete applications (site exporter, scraper demos) live under `codex-ir-app`.
- New dependencies such as PDF or ePub renderers must be placed behind ports/interfaces.

## Resources

- `codex-ir-core/src/main/resources/stopwords_en.txt`, `stopwords_es.txt` — used by `Normalizers`
- `codex-ir-core/src/main/resources/logback.xml` — console logging at INFO level
- `Normalizers.StopWordNormalizer` resolves stop-word files via `IR_STOPWORDS_PATH` env var, then `classpath:` or `file:` prefix, falling back to `/stopwords.txt`
- HTML fixture files for web tests: `codex-ir-web/src/test/resources/fixtures/`
- Every package has a `package-info.java` describing its purpose — read before adding types to a new package.
- Deep work: consult `docs/knowledge/index.md` for the CKF knowledge bundle (phase plans, ADRs, reviews, engineering logs).

## Reports and generated output

- `reports/` contains generated JSON crawl outputs — do not edit.
- `docs/apps/site-exporter/ENGINEERING_LOG.md` — read before modifying the site exporter.
- `docs/tasks/` — task specifications and test cases for major features.
