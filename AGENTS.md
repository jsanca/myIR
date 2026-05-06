# myIR — Agent Operating Instructions

## Build & Test

- **Compile:** `mvn compile`
- **All tests:** `mvn test`
- **Single test:** `mvn test -Dtest=RankersTest`
- **Full verification:** `mvn compile && mvn test-compile && mvn test`
- **Requires Java 25.** Maven is pinned to source=25 target=25 in `pom.xml`. No other JDK version will work.
- **Playwright prerequisite:** `npx playwright install` must be run once before any crawler demo or test that touches `WebPageFetcher`. Without browser binaries, the Playwright dependency will fail at runtime.

## Architecture

### Package layout
Single-module Maven project (`pom.xml` at root). All production code under `src/main/java/codex/ir/`. Tests live flat under `src/test/java/codex/ir/` (no sub-packages for tests, mirrors the source package names). Entry point: `src/main/java/codex/Main.java`.

### No module system
This project uses the classpath, not the Java module system. There is no `module-info.java`. Do not add one unless explicitly requested.

### Interface + Factory pattern
Every domain concept follows: `Xxx` interface + `Xxxes` static factory class. Implementations are `private static final` inner classes of the factory. The concrete types must never leak into public APIs — callers use the interface type exclusively. Examples: `Corpus`/`Corpora`, `Tokenizer`/`Tokenizers`, `Normalizer`/`Normalizers`, `Ranker`/`Rankers`, `Indexer`/`Indexers`, `Searcher`/`Searchers`, `Vectorizer`/`Vectorizers`, `Vocabulary`/`Vocabularies`, `Similarity`/`Similarities`, `DocumentVectorStore`/`VectorStores`.

### Domain types
Core data types use Java **records**: `Document`, `Posting`, `SearchResult`, `SparseDocumentVector`, `CorpusStatistics`, `WebPage`, `WebCrawlingConfig`, `VTConfig`, `WebHttpResponse`, `SimilarityResult`, `SimilarityMatch`, `SparseVectorMetadata`.

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

## Concurrency

- Prefer **Virtual Threads** and **Structured Concurrency** over platform threads or executor pools.
- Use `ScopedValue` over `ThreadLocal`.
- When iterating over a `synchronized` list in tests, synchronize **explicitly** on the list object during the loop.

## Test conventions

- JUnit 5 (`junit-jupiter`), no Mockito, no AssertJ.
- No shared base classes or fixtures — each test is self-contained.
- Tests are named descriptively (e.g. `bm25RankerShouldPenalizeLongerDocumentWhenTermFrequencyMatches`).
- Concurrency tests use `CountDownLatch`, `CopyOnWriteArrayList`, `AtomicInteger` manually.

## Resources

- `src/main/resources/stopwords_en.txt`, `stopwords_es.txt` — used by `Normalizers`
- `src/main/resources/logback.xml` — console logging at INFO level

## Reporting
After each task, report: files changed, tests run, and any architectural questions discovered.
