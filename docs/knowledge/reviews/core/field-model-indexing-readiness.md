---
type: Deep Review
title: Field Model Indexing Readiness Review
description: Baseline assessment of the changes required for field-aware indexing.
tags: [core, indexing, fields, review]
timestamp: 2026-07-05T00:00:00Z
ckf_version: "0.1"
ckf_status: completed
ckf_scope: core
ckf_owner: project
---

# Verdict

The map-based document field model is sufficient for preserving provenance.
True field-aware retrieval requires coordinated changes across preprocessing,
postings, indexing, search, and ranking; strong field types remain premature.

This review captured the baseline before IR-1 and IR-2. Some preprocessing
inefficiencies identified here were resolved by the completed
[IR-1 phase](../../logs/core/ir-1-preprocessed-document.md).

# Current Model at Review Time

`Document.fields` used `Map<String, String>`. Preprocessing joined non-blank
values into one normalized stream and discarded field names. The index,
rankers, weighters, and vectorizer therefore had no field context.

# Strengths

- The whole-document aggregation contract was explicit and test-covered.
- Documents without fields retained raw-content compatibility.
- Field handling was isolated at the preprocessing boundary.
- Core remained independent of web-specific field names.

# Findings

## Repeated Token Serialization

Preprocessing joined field text, tokenized and normalized it, serialized tokens
to `normalizedContent`, and downstream stages split the string again. IR-1
removed the lexical and weighting repetitions by carrying normalized tokens and
cached term frequencies.

## Missing Field Identity

`Posting` and `InvertedIndex.add` have no field parameter. The current index
cannot distinguish a title occurrence from a body occurrence.

## Flat Lexical Consumption

Lexical indexing consumes an aggregate token sequence. Field-aware insertion
cannot begin until preprocessing preserves independent field sequences.

## Ranking Has No Field Context

`Ranker.score(term, posting)` has no field weights or per-field frequencies.
Field boosts must be introduced as a later, neutral-by-default extension.

## Vector Search Is Whole-Document

Document vectors use aggregate term weights and query every stored sparse
vector. Field-aware vectors and approximate search are separate concerns.

# Recommendation

1. Keep string field names and the existing `Document` shape.
2. Preserve normalized field token sequences in a composed pipeline artifact.
3. Keep aggregate tokens for compatibility.
4. Stabilize provenance before selecting a posting representation.
5. Add ranking weights only after field-aware postings exist.
6. Defer BM25F and per-field corpus statistics until evaluation demonstrates
   a retrieval-quality need.

# Risks

- Adding field identity directly to `Posting` has a broad compatibility impact.
- Maintaining both flat and field-aware paths can create divergent semantics.
- Premature field taxonomies would leak application concepts into core.
- Field boosts without an evaluation harness can create imaginary improvements.

# Relationships

- Informs [ADR-005](../../decisions/adr-005-field-aware-indexing.md).
- Its preprocessing findings were addressed by [IR-1](../../logs/core/ir-1-preprocessed-document.md).
- Its provenance recommendation is implemented by [IR-2](../../phases/core/ir-2-field-provenance.md).
