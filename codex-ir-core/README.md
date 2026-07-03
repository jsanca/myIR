# codex-ir-core

Core Information Retrieval engine — tokenization, normalization, indexing, ranking, search, and sparse vector retrieval. This module has zero dependencies on other myIR modules; it only requires SLF4J for logging.

## JPMS Module

`codex.ir.core` — exports all packages except `codex.ir.util` (internal utility, not part of the public API).

## Packages

| Package | Responsibility |
|---------|---------------|
| `codex.ir` | `Document` record — the fundamental data unit of the IR engine |
| `codex.ir.tokenizer` | Splits text into indexable tokens (`Tokenizers.whitespace()`) |
| `codex.ir.normalizer` | Token normalization: lowercase, accent folding, stop-word removal, composite chains |
| `codex.ir.concurrent` | Virtual-thread executor, debouncer for cooperative statistics refresh |
| `codex.ir.corpus` | `Corpus` — document collection with aggregate statistics |
| `codex.ir.corpus.vector` | `Vocabulary` — maps normalized terms to stable integer dimension IDs |
| `codex.ir.indexer` | Indexing pipeline: `DocumentPreprocessor` + lexical and/or vector stages |
| `codex.ir.ranking` | Ranking strategies: binary, TF-IDF, BM25 (with configurable k1/b) |
| `codex.ir.search` | Lexical searcher (`SimpleSearcher`) and vector searcher (`VectorSearcher`) |
| `codex.ir.vector` | Sparse vector types, vectorization, and cosine similarity |
| `codex.ir.vector.store` | In-memory sparse document vector persistence |
| `codex.ir.weight` | Term weighting strategies: raw TF and TF-IDF |
| `codex.ir.util` | `TermWeightingUtils` — IDF and sublinear TF math (internal, not exported) |

## Key Patterns

### Interface + Factory

Every domain concept follows the same shape:

```
Corpus  +  Corpora     ← factory (static methods)
Ranker  +  Rankers     ← factory
Indexer +  Indexers    ← factory
```

Implementations are `private` or package-private inner classes of the factory. Callers use the interface type exclusively.

### Document Model

`Document` carries `rawContent`, `normalizedContent`, `fields` (structured key-value pairs), and `DocumentMetadata` (term frequencies, length, unique terms, extensible attributes). When `fields` is non-empty, the `DocumentPreprocessor` aggregates field values into the normalization stream instead of `rawContent`.

## Resources

- `src/main/resources/stopwords_en.txt`, `stopwords_es.txt` — stop-word lists for `Normalizers.StopWordNormalizer`
- `src/main/resources/logback.xml` — console logging at INFO level

Stop-word files are resolved via `IR_STOPWORDS_PATH` env var, then `classpath:` or `file:` prefix, falling back to `/stopwords.txt`.

## Test Conventions

- JUnit 5 (`junit-jupiter`) — no Mockito, no AssertJ
- Tests mirror source package structure
- Descriptive method names (e.g. `bm25RankerShouldPenalizeLongerDocumentWhenTermFrequencyMatches`)
- Concurrency tests use `CountDownLatch`, `CopyOnWriteArrayList`, `AtomicInteger` manually

## Common Commands

```shell
# Build
mvn compile -pl codex-ir-core

# All core tests
mvn test -pl codex-ir-core

# Single test
mvn test -pl codex-ir-core -Dtest=codex.ir.ranking.RankersTest
```
