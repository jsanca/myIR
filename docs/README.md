

# myIR – Mini Information Retrieval Engine

This project is a **didactic implementation of a small search engine written in Java**, inspired by classic Information Retrieval (IR) systems such as Lucene.

The goal is not to compete with Lucene but to **reconstruct the core concepts of a search engine from scratch** for learning, experimentation, and future research projects.

---

# Project Goals

1. Rebuild the foundations of classic Information Retrieval.
2. Implement a minimal but functional inverted index.
3. Experiment with ranking algorithms such as TF and TF‑IDF.
4. Create a foundation for more advanced experiments such as semantic graphs and embeddings.

This project is intentionally simple and incremental.

---

# Development Roadmap

## Phase 1 – Basic Inverted Index (MVP)

Goal: Index a set of `.txt` documents and allow simple term searches.

Components:

- `Document`
- `Tokenizer`
- `Normalizer`
- `StopWordsFilter`
- `InvertedIndex`
- `Indexer`
- `SearchEngine`

Pipeline:

```
Documents (.txt)
      ↓
Tokenizer
      ↓
Normalization
      ↓
Stop Words Filter
      ↓
Inverted Index
```

Example index structure:

```
java   → doc1, doc3
search → doc2
vector → doc1
```

Example query:

```
query: "java"
result: doc1, doc3
```

---

## Phase 2 – Term Frequency

Extend the inverted index to store **term frequencies per document**.

Example:

```
java → { doc1:3, doc3:7 }
```

This allows the engine to begin ranking results.

---

## Phase 3 – TF‑IDF Ranking

Introduce ranking based on:

- Term Frequency (TF)
- Inverse Document Frequency (IDF)

This enables scoring documents according to relevance.

Example query:

```
"java search engine"
```

Documents will be ranked by total score.

---

## Phase 4 – Vector Space Model

Represent documents and queries as vectors.

Use **cosine similarity** to compute document relevance.

This prepares the system for more advanced IR experiments.

---

## Phase 5 – Future Experiments

Possible extensions:

- Semantic document maps
- Graph‑based text analysis
- TextRank summarization
- Word embeddings
- Hybrid IR + LLM systems

---

# Proposed Package Structure

```
jsanca.gh.ir

model
  Document
  Posting
  SearchResult

text
  Tokenizer
  Normalizer
  StopWordsFilter

index
  InvertedIndex
  Indexer

search
  SearchEngine
  RankingService

Main
```

---

# Philosophy

This project embraces a **"build it from scratch" approach**.

The objective is clarity and experimentation rather than performance.

Understanding the internal mechanics of search engines provides a strong
foundation for modern systems such as:

- Lucene
- Elasticsearch
- Vector databases
- LLM retrieval systems

---

# Long‑Term Vision

Once the core IR engine is complete, it can evolve into experiments such as:

- semantic clustering
- document similarity graphs
- hybrid lexical + semantic search

In other words, this small project becomes a **playground for Information Retrieval research**.

# myIR – Mini Information Retrieval Engine

This project is a **didactic yet evolving Information Retrieval (IR) engine written in Java**, inspired by systems like Lucene.

Unlike a toy example, this project has grown into a **modular, extensible playground** that explores real-world concerns such as crawling, concurrency, ranking, and lifecycle management.

The goal is not to compete with production systems, but to **understand and rebuild their core ideas from first principles**.

---

# Project Goals

1. Rebuild the foundations of classic Information Retrieval.
2. Implement a functional inverted index with ranking.
3. Explore real-world crawling and ingestion pipelines.
4. Experiment with ranking algorithms (TF‑IDF, BM25).
5. Serve as a foundation for future AI/LLM retrieval experiments.

---

# Current Capabilities

## Indexing

- In-memory corpus
- Inverted index with postings
- Term frequency tracking
- Incremental statistics

## Text Processing

- Tokenization
- Normalization
- Stop words filtering

## Ranking

- Binary ranking (term presence)
- TF‑IDF
- **BM25 (recommended for real-world usage)**

## Crawling (New)

- Concurrent web crawling using **Virtual Threads (Java 21)**
- Breadth-First traversal strategy
- Backpressure using semaphores
- Pluggable fetchers (Jsoup today, Playwright later)
- Link extraction and traversal
- Domain and depth controls

## Runtime & Lifecycle (New)

- `CrawlerRuntime` for managing shared resources
- Fetcher reuse via `WebPageFetcherRegistry`
- Config-based scoping (`configId`)
- Explicit lifecycle management (`close()`)

---

# Architecture Overview

## Indexing Pipeline

```
Documents / Crawled Pages
        ↓
Tokenizer
        ↓
Normalizer
        ↓
Stop Words Filter
        ↓
Inverted Index
        ↓
Ranker (TF‑IDF / BM25)
```

## Crawling Pipeline

```
Seed URLs
     ↓
Traversal Strategy (BFS)
     ↓
Fetcher (Jsoup / future Playwright)
     ↓
Link Extraction
     ↓
DocumentSource
     ↓
Ingestion → Index
```

---

# Key Concepts

## Separation of Concerns

- **Fetcher** → retrieves content
- **Strategy** → controls traversal
- **Registry** → manages reusable resources
- **Runtime** → owns lifecycle

## Reusability & Performance

- Fetchers are reused per configuration (`configId`)
- Avoids recreating HTTP clients and thread pools

## Concurrency

- Virtual Threads for I/O scalability
- Semaphore-based backpressure

## Lifecycle Management

- Resources implement `AutoCloseable`
- Runtime coordinates shutdown

---

# Development Roadmap

## Phase 1 – Basic Inverted Index ✔

## Phase 2 – Term Frequency ✔

## Phase 3 – TF‑IDF ✔

## Phase 4 – Vector Space Model (in progress)

## Phase 5 – Crawling & Runtime ✔ (current focus)

## Phase 6 – Future Experiments

- Vector representations (embeddings)
- Hybrid search (lexical + semantic)
- Graph-based document analysis
- Integration with LLM pipelines

---

# Philosophy

This project embraces a **"build it from scratch" approach**.

The focus is on:

- understanding systems deeply
- making trade-offs explicit
- experimenting with architecture

Rather than hiding complexity, the project **surfaces it and manages it explicitly**.

---

# Long-Term Vision

This project aims to evolve into a **research playground for Information Retrieval and AI systems**, bridging:

- classical IR (TF‑IDF, BM25)
- modern vector search
- LLM-based retrieval pipelines

---

# Notes

The codebase is intentionally iterative. Some components may evolve or be simplified over time as better abstractions emerge.

This is part of the learning process.

---

# Author

Built as a learning and experimentation project by a Java architect exploring the intersection of:

- search engines
- distributed systems
- and modern AI workflows