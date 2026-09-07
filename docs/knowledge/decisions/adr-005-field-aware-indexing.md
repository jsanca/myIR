---
type: Architecture Decision
title: ADR-005 — Future Field-Aware Indexing and Field Weighting
description: Preserve whole-document compatibility while introducing field provenance incrementally.
tags: [core, indexing, fields, ranking]
timestamp: 2026-07-05T00:00:00Z
ckf_version: "0.1"
ckf_status: accepted
ckf_scope: core
ckf_owner: project
---

# Status

Accepted and implemented incrementally through lexical field boosting. IR-2
preserved provenance, IR-3 added posting field frequencies, and IR-4 added
query-time lexical weighting. Field-restricted query syntax, per-field
statistics/BM25F, and field-aware sparse vectors remain open choices rather
than implied commitments.

# Context

myIR accepts structured document fields such as `title`, `body`, and `summary`.
The accepted whole-document contract aggregates non-blank field values before
indexing. Downstream postings, vectors, rankers, and corpus statistics therefore
operate on one undifferentiated content stream.

The completed [IR-2 phase](../phases/core/ir-2-field-provenance.md) preserves
field token provenance as an analysis artifact. The completed IR-3 and IR-4
slices extend lexical postings and ranking without replacing the
whole-document compatibility path.

# Decision Direction

Do not perform a big-bang conversion to field-aware retrieval. Preserve the
whole-document path while adding field capabilities in independently testable
slices:

1. Preserve normalized per-field tokens in preprocessing artifacts. **Complete.**
2. Introduce field-aware postings after provenance is stable. **Complete.**
3. Add neutral field weights before adding ranking boosts. **Complete.**
4. Consider BM25F only after per-field frequencies and lengths are proven useful. **Open.**

# Options

## Preprocessing Boosts

Repeat or weight important field tokens before indexing. This has low structural
cost, but distorts document length and cannot support field-specific queries.

## Field Identity in Posting

Add field identity to each posting. This supports query-time weighting but
changes posting accumulation, ranking, search, and compatibility surfaces.

## Separate Index per Field

Retain the existing posting shape while maintaining one index per field. This
simplifies field-specific lookup but complicates lifecycle and result merging.

## Fielded Keys in One Index

Key postings by field and term. This keeps one index instance but makes the
current `getPostings(term)` contract ambiguous.

## BM25F

Use field-specific frequencies, lengths, normalization, and boosts. This is the
most expressive option and also the most invasive; it depends on one of the
field-aware index models above.

# Consequences

## Positive

- Existing lexical and vector behavior remains stable during migration.
- Field provenance can be tested without committing to an index representation.
- Ranking complexity is deferred until evidence justifies it.

## Negative

- Field-specific query syntax, field statistics, and BM25F remain unavailable.
- Compatibility paths temporarily coexist with richer analysis artifacts.
- A later posting/index decision still affects several core contracts.

# Related Knowledge

- Depends on the existing whole-document aggregation decision in
  [`ADR-004`](../../adrs/ADR-004.md).
- Informed by the [Field Model Indexing Readiness Review](../reviews/core/field-model-indexing-readiness.md).
- Implemented incrementally by [IR-2: Field Provenance](../phases/core/ir-2-field-provenance.md), [IR-3](../logs/core/ir-3-field-aware-postings.md), and [IR-4](../logs/core/ir-4-field-aware-ranking.md).
- Builds on the completed [IR-1 preprocessing artifact](../logs/core/ir-1-preprocessed-document.md).
