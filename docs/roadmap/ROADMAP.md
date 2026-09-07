# Roadmap

This document records committed project direction. Keep proposals and uncommitted ideas under `future/` rather than presenting them as planned work.

## Existing Roadmap Sequence

| Item | Outcome | Status | Target | References |
| --- | --- | --- | --- | --- |
| IR-5 — Score Explanation | Explain ranking signals, weights/boosts, contributions, and final score; observability groundwork for later rank fusion | Candidate — not selected | Human planning session | [candidate map](../knowledge/research/candidate-capabilities.md) |
| IR-6 — Evaluation Harness | Evaluate retrieval-quality changes using judged queries and metrics | Candidate — not selected | After roadmap review | [candidate map](../knowledge/research/candidate-capabilities.md) |

## Recently Completed

| Item | Status | Evidence |
| --- | --- | --- |
| IR-0 — Read/Write Boundary | Complete | [CKF log](../knowledge/logs/core/ir-0-read-write-boundary.md) |
| IR-0.5 — Batch Index Build | Complete | [CKF log](../knowledge/logs/core/ir-0-5-batch-index-build.md) |
| IR-1 — PreprocessedDocument | Complete | [CKF log](../knowledge/logs/core/ir-1-preprocessed-document.md) |
| IR-2 — Field Provenance | Complete | [CKF log](../knowledge/logs/core/ir-2-field-provenance.md) |
| IR-3 — Field-Aware Postings | Complete | [CKF log](../knowledge/logs/core/ir-3-field-aware-postings.md) |
| IR-4 — Field-Aware Ranking / Boosting | Complete | [CKF log](../knowledge/logs/core/ir-4-field-aware-ranking.md) |

## Non-committed research and architectural direction

Stemming, n-grams, positional retrieval, BM25 tuning, field-aware vectors, rank fusion, structural corpus analysis, dense vectors/SIMD, and disk-backed storage are recorded in the [candidate map](future/README.md). They are not implicitly scheduled by their presence there.
