# Retrieval Research Candidate Map

This page preserves candidate capabilities without turning them into a single commitment or delivery order. The committed-roadmap source is [ROADMAP.md](../../roadmap/ROADMAP.md).

| Classification | Candidates |
| --- | --- |
| Existing roadmap sequence | IR-5 Score Explanation; IR-6 Evaluation Harness |
| Near/mid-term retrieval candidates | stemming; character n-grams/sub-word tokenization; phrase/proximity retrieval using stored positions; BM25 parameter exposure/tuning; field-aware vector indexing |
| Longer-term research | rank fusion; paragraph/document and corpus centroids; clustering; graph and TreeMap/hierarchical representations; concept extraction/classification; extractive summarization; dense vectors; suitable Java Vector API/SIMD use |
| Architectural direction | disk-backed corpus, index, vocabulary, and vector-store implementations when evidence justifies them |

IR-5 is observability groundwork: an eventual explanation should connect a result to the scoring signals, weights/boosts, contributions, and final (later possibly fused) rank. IR-6 is empirical-quality groundwork: it should allow retrieval changes to be compared against judged queries rather than only tested for formula correctness. Neither item is selected as the next iteration by this classification.

## Open design question

Sparse vector indexing currently uses the aggregated document representation. Whether it should retain field provenance is an explicit design/research question, not a present defect. Dense-vector computation is separate future work; it does not imply changing today’s sparse representation to SIMD.

## Evidence

- [State of the Art assessment](../../engineering/agents/reports/myir-state-of-the-art-2026-09.md#11-roadmap-vs-implementation)
- [Current system boundaries](../architecture/current-system.md)
