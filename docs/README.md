

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
