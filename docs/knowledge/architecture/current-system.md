# Current System and Architectural Boundaries

myIR is an in-memory Information Retrieval learning and experimentation platform. It deliberately separates reusable retrieval, web acquisition, and application concerns:

```text
codex-ir-core  ←  codex-ir-web  ←  codex-ir-app
```

The Maven dependency direction is `app → web → core`. `core` stays domain-agnostic; `web` supplies reusable crawling, ingestion, canonicalization, and extraction capabilities; applications compose those capabilities for specific use cases. The site exporter is intentionally independent of search indexing. Its lack of integration with the IR engine is not an architectural gap or an implied roadmap commitment.

## Current retrieval model

IR-0 through IR-4 are complete: snapshot read/write isolation, batch index builds, preprocessing artifacts, field provenance, field-aware postings, and field-aware lexical boosting. Lexical retrieval supports binary, TF-IDF, and BM25 ranking. Sparse TF/TF-IDF vectors support cosine retrieval by linear scan.

Documents with usable structured fields retain an aggregated whole-document representation for statistics and sparse vector indexing. Lexical postings also retain field frequencies, which lexical rankers can boost at query time. Field-aware vector indexing is deliberately unresolved: the question is whether future vector representations should retain or exploit provenance, and which retrieval model would justify it.

Positions are stored in lexical postings but are not consumed during query evaluation. They are intentional preparation for possible phrase/proximity search, passage retrieval, and snippets/KWIC—not dead data.

## Intentional constraints and directions

All current core stores are in memory by design; see [ADR-003](../../adrs/ADR-003.md). Future persistence must not assume core contracts are permanently memory-only. Sparse vectors are lexical/statistical representations and require no Java Vector API rewrite. Dense-vector work, and any appropriate SIMD use for fixed-width dense numerical operations, are future research directions.

WordPress/WooCommerce extraction is intentional application/use-case specialization. It must not make the IR core WooCommerce-aware. Reusable APIs should be assessed separately for accidental specialization when evidence identifies it.

## Evidence

- [State of the Art assessment](../../engineering/agents/reports/myir-state-of-the-art-2026-09.md)
- [IR-0 through IR-4 engineering logs](../index.md#core)
- [ADR-003](../../adrs/ADR-003.md) and [ADR-004](../../adrs/ADR-004.md)
