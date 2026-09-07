# MyIR Interview Deep-Dive Notes

**Audit basis:** repository working tree reviewed 2026-09-03. `mvn test` passed: **359 tests, 0 failures, 0 errors**. This is an evidence brief, not a product claim sheet.

## Status vocabulary

| Label | Meaning in this brief |
| --- | --- |
| **Implemented** | Present in the audited working tree and covered by code/tests where cited. |
| **Experimentally exercised** | Demonstrated by unit/integration-style tests or runnable demos; not necessarily evaluated on judged production queries. |
| **Planned/proposed** | A roadmap, task, ADR, or future-document intention; not a shipped behavior. |
| **Open** | The repository identifies the choice, but does not yet make or validate it. |

## Ground truth in one paragraph

MyIR is a Java 25, in-memory IR laboratory. Today it has a positional inverted index, binary/TF-IDF/BM25 lexical scoring, sparse vectors over a local vocabulary, TF or TF-IDF weighting, sparse cosine search, crawling/ingestion, and immutable corpus/index snapshots for reads. The “vector” implementation is **not a neural embedding system**: it is a sparse bag-of-normalized-terms representation. Lexical and vector indexes can be built from one preprocessing pass, but they are queried independently and their results are never merged. A `RankingContext` can boost lexical matches by field provenance in the current working tree; it is not BM25F, does not apply to vector search, and does not create field-restricted queries.

## 1. Why does MyIR use or explore hybrid retrieval?

### 30-second interview answer

MyIR does not use hybrid retrieval in production today; it is explicitly planned. It already maintains lexical and sparse-vector representations from the same normalized document, which makes a fair comparison or eventual hybrid layer possible. The motivation is complementary evidence: lexical scoring is exact, inspectable, and strong for rare terms; the current vector path is another term-weighted cosine view. Future dense embeddings would extend recall for semantic mismatch, but that is still a proposed direction and must be evaluated rather than assumed to help.

### 2-minute technical deep dive

The combined indexer runs one canonical preprocessing stage, then sends the same preprocessed document to a positional inverted index and a sparse vector store. That eliminates an easy source of false hybrid conclusions: lexical and vector paths do not see different cleaned text. Lexical search unions postings for normalized query terms and scores them with Binary, TF-IDF, or BM25. Sparse-vector search weighs normalized query terms, creates a vocabulary-indexed sparse vector, scores every stored vector with cosine similarity, and applies a threshold.

Those paths are complementary representations, but neither is a semantic encoder. Sparse cosine still relies on shared normalized terms, so it does not solve vocabulary mismatch the way a dense embedding model might. The sensible MyIR claim is therefore: *the architecture creates a controlled baseline for hybrid experiments*, not that MyIR has already demonstrated hybrid relevance gains. The planned sequence is especially defensible: add an evaluation harness before claiming field boosts or a hybrid merger improve relevance; dense vectors and hybrid retrieval are later tasks.

### Evidence from MyIR

- `codex-ir-core/src/main/java/codex/ir/indexer/Indexers.java` — `lexicalAndVector` and `batchLexicalAndVector` share preprocessing; batch mode gives all vectors one corpus-statistics snapshot.
- `README.md` — separate retrieval paths and the explicit limitation, “Hybrid lexical/vector ranking is not implemented.”
- `docs/README.md` — hybrid search is planned; dense vectors are longer-term.
- `docs/tasks/core/Plan01-Roadmap-Field-AwareIR.md` — IR-6 evaluation harness, IR-8 dense vectors, IR-9 hybrid retrieval.
- `codex-ir-core/src/test/java/codex/ir/indexer/BatchIndexerTest.java` — lexical and vector paths both work after a shared batch build.

### Current limitations / unknowns

- No hybrid query executor, candidate union, fusion algorithm, normalization policy, or hybrid latency measurement.
- No dense embeddings, external embedding model, ANN index, or semantic-recall experiment.
- No judged dataset establishes that either sparse vectors or a hybrid would beat BM25.

### Likely follow-up questions

- “Why combine scores rather than use vector retrieval only for candidate generation?”
- “How would you normalize BM25 and cosine scores?”
- “What query segments benefit from each retriever?”
- “What offline metric and judgment set would approve the merger?”

## 2. How does MyIR evaluate retrieval quality?

### 30-second interview answer

It currently evaluates retrieval *behavior and scoring invariants*, not end-to-end relevance quality. Tests check normalization, indexing, TF-IDF/BM25 behavior, vector ranking on small synthetic fixtures, snapshot isolation, field-boost invariants, and batch-IDF consistency. There is no relevance-judged corpus, no precision/recall, MRR, or nDCG reporting. The planned IR-6 harness is the right next step: fixtures, expected query results, and at least precision@k/hit@k.

### 2-minute technical deep dive

MyIR’s current tests are useful but should be described accurately. For lexical ranking, `RankersTest` verifies, for example, TF-IDF’s expected IDF behavior and that BM25 prefers a shorter document when term frequency is equal. For vectors, tests show a rare or exact query term puts the expected synthetic document first. The field-ranking tests prove a neutral context preserves baseline scores and a configured title boost can reorder a controlled pair. Snapshot tests prove a searcher sees exactly its captured corpus/index state. These are regression and correctness tests, not human relevance judgments.

No checked-in qrels map query/document pairs to graded relevance. No corpus-level run compares rankers under common judgments. Therefore the defensible answer to “Did field boosts improve relevance?” is: *they produce the intended controlled ordering, but no evaluation harness yet validates a user-quality improvement*. The task roadmap explicitly recognizes this risk: “Prevent imaginary improvements.” A proposed next step is to version a small crawl-derived fixture corpus, define queries and graded judgments, report Recall@k/Precision@k, MRR, and nDCG@k, then inspect per-query regressions before tuning.

### Evidence from MyIR

- `codex-ir-core/src/test/java/codex/ir/ranking/RankersTest.java` — scoring and length-normalization checks.
- `codex-ir-core/src/test/java/codex/ir/search/VectorSearcherTest.java` and `.../indexer/BatchIndexerTest.java` — controlled vector-ranking and batch-IDF behavior.
- `codex-ir-core/src/test/java/codex/ir/ranking/FieldAwareRankingTest.java` — neutral compatibility and title-boost ranking checks.
- `docs/tasks/core/Plan01-Roadmap-Field-AwareIR.md` — IR-6 explicitly proposes fixtures, expected top-k, precision@k, and hit@k.
- `docs/knowledge/reviews/core/field-model-indexing-readiness.md` — warns that boosts without an evaluation harness can create imaginary improvements.

### Current limitations / unknowns

- No relevance judgments, offline metrics, held-out queries, error taxonomy, or A/B/online feedback loop.
- Existing tests are largely small and synthetic; they establish implementation correctness, not precision/recall on crawled pages.
- There is no documented acceptance threshold for a ranking change.

### Likely follow-up questions

- “Who judges relevance, and is it binary or graded?”
- “How would you prevent tuning to the same queries you report?”
- “Which metric optimizes the product objective—recall, first-result quality, or diversity?”
- “How will you analyze statistically significant regressions?”

## 3. What happens when lexical and semantic retrieval disagree?

### 30-second interview answer

Today, nothing automatic happens because MyIR has no semantic/dense retriever and no hybrid merger. Lexical and sparse-vector searches return independent ordered lists. A caller may inspect both, but the repository defines no tie-breaker, fallback, or confidence rule. The proposed design is to log the disagreement, use a judged dataset, and select a candidate-generation-plus-reranking or rank-fusion policy based on measured outcomes—not intuition.

### 2-minute technical deep dive

This is an important place not to overstate the project. `Searchers.lexical(...)` constructs a `SimpleSearcher`; `Searchers.vector(...)` constructs a `VectorSearcher`. Both return `List<SearchResult>`, but no class invokes both paths for one query. The lexical score is a sum of ranker contributions; vector score is cosine similarity. Those numbers have different scales and distributions, so direct addition would be unjustified even if a caller manually combined lists.

The current sparse vector path is also not semantic retrieval: it cannot rank a document with no shared normalized term dimensions above the similarity threshold. The disagreement scenario becomes meaningful only after introducing a dense retriever or a hybrid candidate set. MyIR’s first proposed policy should be explicit: run independent retrievers, retain source/rank/score and query analysis, deduplicate by document ID, and evaluate choices such as reciprocal-rank fusion (RRF), calibrated weighted fusion, or lexical-first candidate generation plus a reranker. A “semantic wins” rule would be a product decision, not an existing algorithm.

### Evidence from MyIR

- `codex-ir-core/src/main/java/codex/ir/search/Searchers.java` — separate lexical and vector factories only.
- `SimpleSearcher.java` and `VectorSearcher.java` — independent implementations and independent score semantics.
- `README.md` and `docs/README.md` — hybrid ranking planned/not implemented; vectors are sparse.
- `docs/tasks/core/Plan01-Roadmap-Field-AwareIR.md` — dense/hybrid work is future of the documented sequence.

### Current limitations / unknowns

- No semantic retriever, hybrid list, deduplication, source attribution, fusion, or tie policy.
- No calibrated confidence model and no evidence for a query-class routing rule.
- `SearchResult` has a single score and matched terms; it does not retain retriever provenance or a score breakdown.

### Likely follow-up questions

- “Would you use RRF, score calibration, learned fusion, or a cross-encoder?”
- “How do you avoid a low-quality semantic result displacing an exact SKU/title hit?”
- “How do you debug a disagreement?”
- “Do you merge candidates before or after applying filters?”

## 4. How are results ranked or merged?

### 30-second interview answer

Within each implemented path, MyIR scores every candidate and sorts descending. Lexical search uses union semantics across query terms and sums Binary, TF-IDF, or BM25 term contributions. Sparse vector search linearly scans stored vectors, returns cosine matches strictly above a configured threshold, then sorts descending. There is no cross-retriever merge. In the current working tree, lexical field boosts multiply a term contribution by a frequency-weighted field factor; that is a lightweight boost, not BM25F.

### 2-minute technical deep dive

For lexical search, each query token is normalized with the same analysis pipeline used at indexing. The searcher reads postings from an immutable `IndexSnapshot`. Documents that match any normalized term become candidates—there is no AND/minimum-should-match operator. It accumulates per-term contributions by document ID and sorts the resulting `SearchResult`s. Binary gives presence credit; TF-IDF uses sublinear TF and classic IDF; BM25 uses `k1=1.2`, `b=0.75`, document length, average document length, and a BM25 IDF function.

For sparse vectors, documents are represented as integer vocabulary dimensions with TF or TF-IDF weights and precomputed norms. Search creates a query vector, compares it with every document vector, and retains scores greater than the threshold (not greater-or-equal). This is an exhaustive scan, so it is correct for the stored set but does not provide approximate-nearest-neighbor scaling.

Field-aware lexical boosting is implemented in the audited working tree. A posting retains per-field term frequencies. `Ranker.score(term, posting, context)` computes the ordinary term score and multiplies it by `sum(fieldFrequency × fieldWeight) / sum(fieldFrequency)`. A neutral context and raw-content documents preserve prior scores. It does not calculate separate field lengths or field IDFs, so it is not BM25F. Older README/ADR language saying field boosts are future is stale relative to the current code and IR-3/IR-4 engineering logs.

### Evidence from MyIR

- `codex-ir-core/src/main/java/codex/ir/search/SimpleSearcher.java` — union candidate selection, score accumulation, descending sort.
- `codex-ir-core/src/main/java/codex/ir/ranking/Rankers.java` — Binary, TF-IDF, and BM25 implementations/constants.
- `codex-ir-core/src/main/java/codex/ir/search/VectorSearcher.java` — full vector-store scan, threshold filter, cosine-ordering.
- `codex-ir-core/src/main/java/codex/ir/ranking/Ranker.java`, `FieldWeights.java`, `RankingContext.java` — current field-boost formula.
- `docs/knowledge/logs/core/ir-4-field-aware-ranking.md` — completed IR-4 scope and exclusions.

### Current limitations / unknowns

- No hybrid merge/normalization and no top-k API, pagination, early termination, query operators, or reranker.
- Sort ties have no documented stable secondary key; concurrent-map iteration may make tie ordering non-deterministic.
- Field weights lack validation for positivity despite their JavaDoc stating positive multipliers; that is a small API-hardening gap.
- No BM25F, per-field lengths/IDF, field-specific query syntax, or vector field boosting.

### Likely follow-up questions

- “Why union semantics—where are AND, phrase, and minimum-match?”
- “How do you calibrate or interpret raw scores?”
- “Why a post-hoc field boost rather than BM25F?”
- “How would you make ties stable and ensure deterministic pagination?”

## 5. How is relevance measured?

### 30-second interview answer

In current code, “relevance” means a ranking score, not a validated user-relevance label. BM25, TF-IDF, and cosine provide retrieval signals; tests check their expected mechanics. MyIR does not currently measure relevance with qrels or precision/recall-style metrics. The next step is a labeled evaluation set and metrics selected for the intended experience, with per-query failure review.

### 2-minute technical deep dive

It helps to distinguish three layers. First, the engine has **scoring functions**: term statistics and length normalization for lexical ranking, or cosine similarity over sparse term-weight vectors. Second, it has **behavioral tests** that assert those functions create expected orderings for fixtures. Third, it lacks **relevance measurement**: independent judgments about whether a result satisfied a user intent at rank k.

For a senior interview answer, say MyIR deliberately has not conflated these. A positive BM25 or cosine score is evidence of match under that model, not proof of relevance. The repository even documents a proposed harness to stop intuitive ranking changes from being declared improvements. A practical proposed design would begin with a small versioned corpus drawn from supported crawls, a query set covering exact names, broad topical terms, navigation-like queries, spelling/normalization cases, and adversarial distractors, plus binary or graded qrels. Report Precision@k/Recall@k or Hit@k for coverage, MRR for first useful result, nDCG@k for graded ordering, and a query-level diff for every model or weighting change.

### Evidence from MyIR

- `Rankers.java`, `Similarities.java`, and `SearchResult.java` — scoring and result metadata currently exposed.
- `docs/tasks/core/Plan01-Roadmap-Field-AwareIR.md` — proposed evaluation harness and precision@k/hit@k.
- `docs/knowledge/reviews/core/field-model-indexing-readiness.md` — explicit warning about unmeasured boosts.
- No `qrels`, evaluation module, metric implementation, or judged retrieval dataset is present in the repository audit.

### Current limitations / unknowns

- No definition of relevance for pages versus products, no grader protocol, and no metric dashboards.
- No click, conversion, or user-feedback instrumentation.
- No known baseline metric values; do not quote quality percentages in an interview.

### Likely follow-up questions

- “Would you use binary or graded relevance?”
- “How would you handle incomplete judgments and pooling?”
- “Which metric would gate a ranking launch?”
- “How would offline relevance relate to online behavior?”

## 6. What corpus/data is MyIR currently using?

### 30-second interview answer

The engine is corpus-agnostic and in-memory. Tests use small synthetic documents. The runnable crawling demo is configured for up to 100 pages at depth 2 from `demo.dotcms.com`, and ADR-003 describes current experiments as on the order of hundreds of crawled pages. Checked-in JSON reports are product-discovery outputs from `syjleathers.com`; they are evidence of crawler/extractor runs, not a relevance-labeled search corpus. There is no canonical benchmark dataset checked in.

### 2-minute technical deep dive

`Document` is the common data model. Documents may enter from raw content or structured fields; nonblank fields are aggregated into whole-document normalized content for the core representations. The `codex-ir-web` module can crawl static pages (optionally dynamically rendered with Playwright when selected), canonicalize URLs, extract metadata, and map pages into documents. The main demo currently wires its crawl to DotCMS, with a bounded crawl configuration and lexical/vector demonstrations. That makes the corpus repeatable only in a loose sense: a live site can change, and the code has no persisted index or corpus snapshot.

There are two generated `reports/product-discovery-report-*.json` artifacts for an S&J Leathers site. They contain discovered page/product data and demonstrate extraction, but they do not supply query-document relevance labels, and the AGENTS instructions say generated reports should not be edited. The repository therefore supports real web ingestion but has no single frozen, benchmark-quality retrieval corpus. For defensible experimentation, capture crawl content plus canonical URLs, analysis configuration, document IDs, and a version/hash before comparing rankers.

### Evidence from MyIR

- `codex-ir-app/src/main/java/codex/scraper/Main.java` — live DotCMS demo, `maxPages(100)`, `maxDepth(2)`, lexical and sparse-vector runs.
- `README.md` — crawling/ingestion and document-processing flow.
- `docs/adrs/ADR-003.md` — in-memory scale described as hundreds of pages in experiments.
- `reports/product-discovery-report-20260530-194840.json` and `...20260612-081347.json` — generated S&J Leathers product-discovery outputs.
- Test fixtures across `codex-ir-core/src/test/java` — synthetic documents used for correctness tests.

### Current limitations / unknowns

- No persisted corpus/index, canonical benchmark, versioned crawl snapshot, or document collection statistics report.
- Live-crawl corpus composition and content quality can drift between runs.
- Product-discovery data is not currently wired as a relevance-test collection.

### Likely follow-up questions

- “How do you reproduce an experiment when the site changes?”
- “What are your corpus language/domain distributions?”
- “How do you deduplicate or handle boilerplate?”
- “What document fields do you index, and what metadata is filterable?”

## 7. Why use embeddings at all?

### 30-second interview answer

Strictly, MyIR does not use learned embeddings today. Its current vectors are sparse TF or TF-IDF representations of normalized terms. Dense embeddings are a documented longer-term option because they can retrieve conceptually related text when exact vocabulary differs. I would introduce them only after establishing a lexical baseline and a judged dataset, because embedding cost and semantic broadening are only worthwhile if they improve measured recall or ranking for this corpus.

### 2-minute technical deep dive

The terminology matters. In MyIR, `Vocabulary` assigns integer dimensions to observed terms, `Vectorizer` builds sparse weight maps, and `Similarities.sparseCosine()` computes cosine only over shared dimensions. This has strong transparency: matched dimensions can be mapped back to terms and contribution values. It also means a query with no overlapping normalized vocabulary cannot gain a vector match. There is no model call, learned vector, tokenizer from an embedding provider, or dense vector store.

Dense embeddings are worth exploring when users express the same intent with different words, when document language is richer than keyword overlap captures, or when semantic candidate recall matters enough to pay indexing/query/model complexity. They should not replace lexical retrieval blindly: exact IDs, product names, rare terms, negation, freshness, and precise constraints often favor lexical signals. The appropriate **next step / proposed design** is to add a model-agnostic embedding port, version vectors by model and preprocessing policy, keep lexical retrieval as a baseline, retrieve a bounded candidate set, and compare dense-only, lexical-only, and fusion/rerank variants under the same qrels and latency budget.

### Evidence from MyIR

- `codex-ir-core/src/main/java/codex/ir/vector/Vectorizers.java`, `SparseDocumentVector.java`, and `Similarities.java` — sparse local vocabulary vectors and sparse cosine, not dense embeddings.
- `docs/README.md` — “embeddings and denser vector models” are longer-term.
- `docs/tasks/core/Plan01-Roadmap-Field-AwareIR.md` — dense vectors are IR-8, after an evaluation harness.
- `docs/tasks/core/Task1-Fields.md` — explicitly excludes dense vectors/embeddings in that phase.

### Current limitations / unknowns

- No embedding model/provider choice, vector dimension, chunking policy, model/version migration, ANN strategy, cost/latency budget, or evaluation result.
- No evidence yet that vocabulary mismatch is a material failure on a target MyIR corpus.
- No multilingual semantic strategy; existing normalizers include English/Spanish resources, but that is not an embedding capability.

### Likely follow-up questions

- “What would be embedded: title, body, chunks, or field-aware composites?”
- “How would you version and reindex embeddings?”
- “How do you preserve exact-match behavior for identifiers?”
- “What ANN recall/latency target would you require?”

## 8. What failure modes have been observed or anticipated for embeddings and semantic retrieval?

### 30-second interview answer

There are no observed dense-embedding failure results because MyIR has not implemented dense embeddings. The implemented sparse-vector path has clearer, observed constraints: it needs shared normalized terms, scans every stored vector, uses a fixed threshold, and has no top-k or hybrid calibration. The anticipated dense-retrieval risks are semantic false positives, loss of exactness, model/preprocessing drift, opaque explanations, vector-memory growth, and latency. MyIR’s documented response is to add evaluation and observability before treating those ideas as improvements.

### 2-minute technical deep dive

For the current sparse path, there are concrete limits. Because cosine uses shared vocabulary dimensions, it cannot bridge synonyms or paraphrases with no common normalized terms. It linearly scans the in-memory vector store and returns all scores above a caller-supplied threshold; threshold behavior is not calibrated per corpus or query. Both lexical and vector search sort all results, and the future document explicitly calls out top-k/pagination/early termination as missing. Incremental vector indexing can also use corpus statistics at the time each document is added; the batch indexer was added to make a batch’s TF-IDF weights consistent.

Dense embeddings would add different failure modes: broad topical resemblance can outrank an exact constraint, models can carry domain/language gaps and stale knowledge, different model versions make vectors incomparable, chunks can lose document context, and poor observability turns retrieval errors into anecdotes. MyIR should also expect raw memory growth: it already keeps raw/normalized content, postings, vocabulary, and sparse vectors in memory; ADR-003 explicitly requires lightweight growth metrics before persistence redesign. The correct claim is not that MyIR has solved these issues—it has identified several architectural risks and has planned evaluation/observability work.

### Evidence from MyIR

- `VectorSearcher.java` — exhaustive scan and fixed `score <= threshold` exclusion.
- `Similarities.java` — sparse cosine only sees shared dimensions.
- `Indexers.java` and `BatchIndexerTest.java` — incremental vs. batch TF-IDF snapshot semantics; batch consistency is covered.
- `docs/Future-Forward.md` — all-match sorting today; top-k/pagination/early termination deferred.
- `docs/adrs/ADR-003.md` — in-memory duplication/growth risk and proposed metrics.
- `docs/README.md` / `Plan01-Roadmap-Field-AwareIR.md` — dense vectors remain future work.

### Current limitations / unknowns

- No empirical dense failure analysis, threshold sweep, latency profile, memory profile, or ANN-recall measurement.
- No retrieval observability beyond normal application logging and `SearchResult` score/matched terms.
- No score explanations, query traces, model metadata, or per-stage latency instrumentation.

### Likely follow-up questions

- “How would you distinguish embedding false positives from bad labels or bad chunking?”
- “How would you choose a threshold or top-k dynamically?”
- “What observability would be emitted for each retrieval stage?”
- “How would you handle embedding-model rollout and rollback?”

## Additional senior-search-engineer topics

| Topic | What MyIR can credibly say today | Next step / proposed design |
| --- | --- | --- |
| **Lexical vs. semantic** | Lexical BM25/TF-IDF and sparse cosine coexist; sparse cosine is still lexical-vocabulary overlap, not semantic dense retrieval. | Evaluate a dense retriever against the lexical baseline on qrels. |
| **Precision vs. recall** | Neither is measured on a judged corpus. Union semantics favors lexical candidate recall across query terms, but no metric validates the tradeoff. | Add qrels; report Recall@k, Precision@k/Hit@k, MRR, nDCG, and per-query deltas. |
| **Top-k selection** | Every matching lexical result and every threshold-passing vector result is sorted in memory. | Bounded heap/top-k, deterministic tie-break, pagination, and candidate-budget instrumentation. |
| **Query/document vectors** | Query and documents use the same normalized term vocabulary; vector weights are TF or TF-IDF and cosine exposes contributing terms. | Separate query/document encoder choices only with a dense-embedding design and evaluation. |
| **Reranking** | Not implemented. `Future-Forward.md` mentions future reranking support. | Candidate generator + reranker interface; retain source score/rank and measure marginal gain and latency. |
| **Indexing** | Positional in-memory inverted index, corpus/index snapshots, combined and batch pipelines; field term frequencies support lexical boosts. | Persisted/versioned corpus snapshots, proper field models/BM25F, operational ingestion statistics. |
| **Latency** | No latency SLOs or measurements. Sparse vector search is O(number of vectors × active dimensions) for a query; full sorting adds cost. | Stage timings, p50/p95, corpus-size curves, candidate counts, and ANN experiments only after evidence. |
| **Relevance judgments** | None. Tests are correctness fixtures. | Create judgment guidelines and versioned qrels; use multiple assessors or adjudication where material. |
| **Observability** | Logging plus `SearchResult` score/matched terms; ADR-003 proposes memory-growth metrics. | Query traces, candidate counts, score components, retrieval source, result-set overlap, error buckets, and resource metrics. |
| **Failure analysis** | Tests cover mechanics and selected edge cases; no retrieval-error review loop. | Store query/run IDs and inspect misses, false positives, zero-result queries, lexical/vector disagreement, and regressions. |

## MyIR Interview Cheat Sheet

1. **MyIR is a Java 25 in-memory IR laboratory, not a Lucene/Elasticsearch replacement.** The focus is transparent first-principles architecture and experiments.
2. **Today’s dependable lexical baseline is positional inverted-index retrieval with Binary, TF-IDF, and BM25.** Search runs against immutable corpus/index snapshots.
3. **Today’s “vector search” is sparse TF/TF-IDF cosine over a MyIR vocabulary—not learned/dense embeddings.** It is explainable through shared term contributions.
4. **Lexical and vector representations share canonical preprocessing in combined indexers.** That makes later comparisons/hybrid experiments less vulnerable to preprocessing drift.
5. **Hybrid retrieval is not implemented.** There is no merger, score normalization, RRF, reranker, or policy for lexical/vector disagreement.
6. **Do not claim semantic retrieval today.** Dense embeddings are a documented future direction (IR-8); hybrid retrieval follows (IR-9).
7. **Current evaluation proves mechanics, not relevance quality.** The suite passed 359 tests in this audit, but there are no qrels, Precision@k/Recall@k, MRR, nDCG, or online metrics.
8. **The planned IR-6 evaluation harness is strategically important.** It proposes fixtures, expected top-k, Precision@k, and Hit@k to prevent “imaginary improvements.”
9. **Field-aware lexical boosting exists in this working tree.** It uses posting field-frequency provenance and a frequency-weighted multiplier; neutral mode retains baseline behavior. It is **not BM25F** and does not affect vector search.
10. **The corpus is real but not benchmarked.** Tests are synthetic; the demo live-crawls DotCMS (bounded at 100 pages/depth 2); generated S&J Leathers reports demonstrate product extraction, not relevance judgments.
11. **Scaling is intentionally deferred.** Storage is in memory; vector retrieval scans all vectors; both paths sort full result sets. Top-k/pagination, latency profiling, ANN, and memory observability are open work.
12. **Best senior-level framing:** establish BM25 and sparse-vector baselines on a frozen crawl plus qrels, instrument failure/latency, then evaluate dense retrieval and fusion/reranking as measured increments.

## Repository consistency note

The current source and completed engineering log for IR-3/IR-4 implement field frequencies and lexical field boosts. `README.md`, `docs/adrs/ADR-004.md`, and the proposed-status header in `docs/knowledge/decisions/adr-005-field-aware-indexing.md` still contain older statements that describe those capabilities as future. Treat the source/tests and the IR-3/IR-4 completion logs as the current working-tree evidence; reconcile the stale documentation before presenting the repository externally.
