

# Future Forward Features

This document captures future-oriented capabilities that we intentionally postponed while building the current core of the IR engine. The goal is to preserve the direction of the project without over-engineering too early.

## Current Context

The engine already has a strong foundation:

- `Corpus`
- `InvertedIndex`
- `DocumentIndexer`
- `DocumentMetadata`
- term frequency stored in metadata
- `TF-IDF`
- `BM25`
- `CorpusStatistics`
- debounced statistics refresh inside the corpus

This means the next features should focus on either:

1. improving document modeling, or
2. improving ingestion with more realistic data sources.

---

## 1. Forward-Oriented Features

### 1.1 Forward Index

A formal `ForwardIndex` may be added later as a complement to the `InvertedIndex`.

Conceptually:

```text
Document -> Terms / Frequencies / Optional Positions
```

Potential value:

- faster access from document to terms
- avoiding repeated reconstruction of document-level views
- enabling richer explainability
- future support for highlighting
- future support for reranking pipelines
- support for field-aware ranking

Why it is not required yet:

- `DocumentMetadata` already stores useful forward-like information
- `Corpus` already retains access to the document
- the project does not yet require a dedicated forward structure for performance or features

Decision:

- keep this as a future feature
- revisit after crawler-driven ingestion and real corpus growth

---

### 1.2 Field-Aware Documents

Right now the engine treats the document mostly as a single searchable content block.

A future improvement is to model a document using multiple fields, for example:

- `title`
- `body`
- `summary`
- `tags`
- `url`
- `author`

This would enable:

- field weighting
- more realistic web/document indexing
- better ranking quality
- cleaner ingestion from HTML and other structured sources

Example future direction:

```text
Document
  -> title
  -> body
  -> metadata
  -> optional structured fields
```

This feature is strategically important because many real ranking systems do not treat all parts of a document equally.

---

### 1.3 Field Weighting

Once documents support explicit fields, ranking can assign different weights to different parts of the document.

Example:

```text
score = 3.0 * titleScore + 1.0 * bodyScore
```

Potential value:

- matches in the title matter more than matches in the body
- tags or keywords may receive higher importance
- ranking becomes more semantically aligned with user intent

This should be introduced only after the document model supports multiple fields in a clean way.

---

### 1.4 Better Explainability

As forward-oriented data structures grow, the engine can later expose richer explanations such as:

- why a document ranked highly
- which fields matched
- term frequencies by field
- which ranking formula contributed most

This is especially useful for debugging and learning.

### 1.5 Search Result Optimization (Top-K / Pagination)

The current search implementation collects all matching documents in memory,
sorts them, and returns the full result set.

This approach is sufficient for small corpora, but it does not scale well as
the number of indexed documents grows.

Future improvements include:

- Top-K retrieval using a priority queue (e.g., keeping only the best N results)
- pagination support (`limit`, `offset`)
- early termination strategies during scoring

Potential value:

- reduces memory usage for large queries
- improves performance by avoiding full sorting of large result sets
- aligns with real-world search engine behavior

Decision:

- keep current implementation for simplicity
- introduce Top-K and pagination when working with larger corpora or real ingestion workloads

---

## 2. Ingestion-Oriented Features

### 2.1 Web Crawler

A crawler is one of the most valuable next steps because it allows the engine to ingest a real corpus.

Potential responsibilities:

- fetch pages
- normalize URLs
- extract HTML text
- capture metadata such as title and URL
- follow links with depth control
- optionally respect robots rules and politeness constraints
- avoid duplicates

Why it matters:

- validates the current indexing pipeline against real content
- exposes practical issues that synthetic tests do not show
- creates the pressure needed to decide whether a formal `ForwardIndex` is really necessary

Current recommendation:

- build the crawler before building a formal forward index

---

## 3. Recommended Order

### Immediate next design step

Before the crawler, it is worth improving the document model so the engine can ingest richer content later.

Recommended order:

1. evolve `Document` to support explicit fields
2. adapt indexing to understand fields
3. keep ranking compatible with the current single-field model
4. build a basic crawler
5. ingest real documents
6. revisit `ForwardIndex` with real evidence
7. later introduce field weighting

---

## 4. Decision Notes

### Should `ForwardIndex` be implemented now?

Not yet.

Reason:

- the engine already has enough forward-like information for the current stage
- a crawler will provide more practical value immediately
- real data should help justify whether a dedicated forward index is needed

### Should fields be added before or after the crawler?

Preferably before the crawler, or at least designed before it.

Reason:

- crawled documents naturally contain structured parts such as title and body
- if the crawler is built first around a single text blob, it may need refactoring later
- adding fields early gives the ingestion pipeline a better target model

Pragmatic interpretation:

- do not overbuild field logic
- but define a clean field-capable `Document` model before the crawler grows

---

## 5. Summary

Near-term direction:

- improve the document model with fields
- then build a crawler
- postpone a formal `ForwardIndex` until real corpus growth justifies it

Guiding principle:

> Prefer real ingestion pressure over speculative internal structures.

---

## 6. Corpus Publication and Write Model (Future Direction)

A design discussion identified a future architectural improvement around how the engine handles mutable corpus state versus published read state.

### Current pragmatic model

For now, the engine keeps the current approach:

- `Corpus` remains directly mutable through `add(...)`
- aggregate counters are updated incrementally
- `CorpusStatistics` is exposed as a debounced immutable snapshot
- synchronization is still used to preserve compound consistency between document storage and aggregate counters

This model is acceptable for the current phase because it keeps the API small and avoids introducing a larger write/session abstraction too early.

### Why this may change later

The current design mixes two kinds of state:

- live mutable corpus state
- published snapshot-style aggregate state

That is a reasonable short-term tradeoff, but it leaves some semantics intentionally loose:

- readers may observe live corpus contents while statistics still reflect the previous published snapshot
- write ordering and publication boundaries are implicit
- synchronization remains part of the correctness model

### Future direction

A future iteration may introduce a more explicit publication and write model.

Possible direction:

- a `CorpusSnapshot` abstraction representing published immutable read state
- a dedicated `CorpusWriter` / `IndexWriter` abstraction for mutation
- queued or batched writes
- explicit publish / flush / commit boundaries

Potential value:

- clearer semantics between live mutation and published read visibility
- less ad hoc synchronization inside `Corpus`
- a design that aligns better with larger ingestion workloads
- a better foundation for vector stores, clustering, and future retrieval pipelines

Decision:

- do not implement `CorpusSnapshot` or `CorpusWriter` yet
- keep the current model for now
- revisit this once ingestion pressure or corpus growth justifies the added abstraction