

# myIR – Information Retrieval Engine in Java

`myIR` is a **didactic but increasingly serious Information Retrieval (IR) engine written in Java**.

It began as a reconstruction of the classical foundations of search engines—tokenization, normalization, inverted indexes, TF‑IDF, cosine similarity—but has evolved into a broader **experimentation platform** for crawling, lexical retrieval, vector retrieval, concurrency, and architectural exploration.

The goal is not to compete with Lucene or Elasticsearch. The goal is to **understand and rebuild core IR ideas from first principles**, while allowing the system to grow into more realistic retrieval experiments over time.

---

# What the Project Is Today

Today, `myIR` is no longer just a toy inverted index.

It already includes:

- lexical indexing and ranking,
- web crawling and ingestion,
- concurrent traversal using **Virtual Threads**,
- sparse vector indexing,
- preliminary vector search,
- URI canonicalization for cleaner crawling identity,
- and explicit architectural decisions documented through ADRs.

This makes the project both:

- a learning vehicle for classical IR,
- and a playground for more advanced retrieval ideas.

---

# Project Goals

1. Rebuild the foundations of classical Information Retrieval.
2. Implement a functional lexical search engine from scratch.
3. Explore real-world ingestion and crawling pipelines.
4. Experiment with sparse vector models and similarity search.
5. Create a foundation for future hybrid IR + AI retrieval systems.

---

# Current Capabilities

## Lexical Retrieval

- In-memory `Corpus`
- In-memory `InvertedIndex`
- Term frequency tracking
- Lexical search through a `Searcher` abstraction
- Ranking algorithms:
  - Binary ranking
  - TF‑IDF
  - **BM25**

## Text Processing

- `Tokenizer`
- `Normalizer`
- document preprocessing pipeline
- normalized content and derived metadata

## Vector Retrieval

- Sparse document vectors
- Vocabulary-backed dimensions
- Cosine similarity for sparse vectors
- Vector indexing pipeline
- Preliminary vector search with matched-term explanations

## Crawling & Ingestion

- Concurrent web crawling using **Virtual Threads**
- Breadth-first traversal strategy
- Configurable crawling limits and scope
- URI canonicalization pipeline
- Pluggable fetchers and registries
- Ingestion through `DocumentSource`, `DocumentMapper`, and `Indexer`

## Runtime & Lifecycle

- `CrawlerRuntime` for shared resource ownership
- explicit lifecycle management with `AutoCloseable`
- reusable fetcher registries
- config-based runtime reuse

## Architecture & Design

- explicit pipelines for preprocessing and indexing
- lexical and vector indexing as separate stages
- factory-based APIs (`Indexers`, `UriCanonicalizers`, etc.)
- architecture decisions captured in ADRs

---

# Current Project Status

The project is currently in this state:

- **Lexical indexing:** implemented
- **TF / TF‑IDF / BM25 ranking:** implemented
- **Web crawling and ingestion:** implemented
- **Sparse vector indexing:** implemented
- **Preliminary vector search:** implemented, still being improved
- **Hybrid search:** planned
- **Observability of memory growth:** planned
- **Disk-backed persistence:** intentionally deferred for now

In short, the lexical IR core is already functional, crawling is real, and vector retrieval is now in the experimental refinement stage.

---

# Architecture Overview

## Indexing Flow

```text
Raw Document / Crawled Page
            ↓
Document Preprocessing
  - tokenize
  - normalize
  - derive metadata
            ↓
PipelineIndexer
   ├─ LexicalIndexer
   └─ VectorIndexer
```

## Lexical Search Flow

```text
Query
  ↓
Tokenizer / Normalizer
  ↓
InvertedIndex lookup
  ↓
Ranker (TF‑IDF / BM25)
  ↓
SearchResult
```

## Vector Search Flow

```text
Query
  ↓
Tokenizer / Normalizer
  ↓
Query weighting
  ↓
Sparse query vector
  ↓
Similarity against stored document vectors
  ↓
SearchResult with matched/contributing terms
```

## Crawling Flow

```text
Seed URLs
   ↓
URI canonicalization
   ↓
Traversal strategy (BFS)
   ↓
Fetcher
   ↓
Extracted page + discovered links
   ↓
Document mapping
   ↓
Indexing pipeline
```

---

# Why Both Lexical and Vector Indexing?

The project intentionally keeps **lexical indexing** and **vector indexing** as separate representations.

This allows experimentation with:

- classical term-based search,
- cosine similarity over sparse vectors,
- document-to-document similarity,
- future hybrid ranking strategies,
- and better explainability of retrieval behavior.

This reflects a core idea in Information Retrieval: different representations provide different strengths.

---

# Roadmap

## Near-Term Focus

- improve vector weighting beyond raw TF
- move vector search toward stronger TF‑IDF-based scoring
- refine result quality on crawled corpora
- continue improving query and document explainability

## Mid-Term

- hybrid lexical + vector retrieval
- document-to-document similarity workflows
- corpus and memory-growth observability
- more domain-aware crawling experiments

## Longer-Term

- embeddings and denser vector models
- graph-based document analysis
- summarization experiments
- LLM-assisted retrieval pipelines
- optional disk-backed or more compact storage strategies

---

# Philosophy

This project embraces a **build-it-from-scratch** philosophy.

The focus is on:

- understanding systems deeply,
- making trade-offs explicit,
- keeping architecture honest,
- and learning by implementing.

Rather than hiding complexity, `myIR` tries to surface it in manageable pieces.

That means some parts of the system are intentionally iterative. Better abstractions often emerge only after the simpler version has been built and exercised.

---

# Notes on Scope

The system currently prefers **in-memory implementations** for core structures such as the corpus, inverted index, vector store, and vocabulary.

This is intentional.

At the current scale, simplicity and learning speed matter more than premature persistence complexity. The project already recognizes that memory observability will become important before any future move to disk-backed structures.

---

# Long-Term Vision

The long-term ambition of `myIR` is to become a **research and experimentation platform** sitting at the intersection of:

- classical IR,
- modern vector retrieval,
- crawling and ingestion,
- and future AI/LLM retrieval systems.

In other words, it is both:

- a way to relearn the foundations,
- and a way to grow toward more advanced retrieval architecture.

---

# Author

Built as a learning and experimentation project by a Java architect exploring:

- search engines,
- concurrency,
- system design,
- and modern AI retrieval workflows.