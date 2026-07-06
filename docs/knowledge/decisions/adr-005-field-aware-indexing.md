---
type: Architecture Decision
title: ADR-005 — Future Field-Aware Indexing and Field Weighting
description: Preserve whole-document compatibility while introducing field provenance incrementally.
tags: [core, indexing, fields, ranking]
timestamp: 2026-07-05T00:00:00Z
ckf_version: "0.1"
ckf_status: proposed
ckf_scope: core
ckf_owner: project
---

# Status

Proposed. Field provenance work is active, but field-aware postings, search,
and ranking are not implemented.

# Context

myIR accepts structured document fields such as `title`, `body`, and `summary`.
The accepted whole-document contract aggregates non-blank field values before
indexing. Downstream postings, vectors, rankers, and corpus statistics therefore
operate on one undifferentiated content stream.

The active [IR-2 phase](../phases/core/ir-2-field-provenance.md) preserves field
token provenance as an analysis artifact without changing that public behavior.

# Decision Direction

Do not perform a big-bang conversion to field-aware retrieval. Preserve the
whole-document path while adding field capabilities in independently testable
slices:

1. Preserve normalized per-field tokens in preprocessing artifacts.
2. Introduce field-aware postings only after provenance is stable.
3. Add neutral field weights before adding ranking boosts.
4. Consider BM25F only after per-field frequencies and lengths are proven useful.

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

- Field-specific search and title boosts remain unavailable initially.
- Compatibility paths temporarily coexist with richer analysis artifacts.
- A later posting/index decision still affects several core contracts.

# Related Knowledge

- Depends on the existing whole-document aggregation decision in
  [`ADR-004`](../../adrs/ADR-004.md).
- Informed by the [Field Model Indexing Readiness Review](../reviews/core/field-model-indexing-readiness.md).
- Implemented incrementally by [IR-2: Field Provenance](../phases/core/ir-2-field-provenance.md).
- Builds on the completed [IR-1 preprocessing artifact](../logs/core/ir-1-preprocessed-document.md).
