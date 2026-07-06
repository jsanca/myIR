---
type: Delivery Phase
title: IR-2 — Field Provenance Artifact
description: Preserve normalized per-field tokens without changing postings, ranking, or whole-document behavior.
tags: [core, indexing, preprocessing, fields]
timestamp: 2026-07-05T00:00:00Z
ckf_version: "0.1"
ckf_status: active
ckf_scope: core
ckf_owner: project
---

# Goal

Preserve field boundaries after normalization while keeping the existing
whole-document indexing and search behavior unchanged.

# Design

Introduce a `FieldAnalyzedDocument` pipeline artifact composed from:

- the existing `PreprocessedDocument` whole-document representation;
- ordered `FieldTokenSequence` values containing a field name and normalized
  tokens.

The richer artifact belongs at the preprocessing boundary. It must not overload
the immutable input `Document` or force field semantics into web-independent
core types.

# Scope

## Included

- Preserve title/body and other non-blank field token sequences independently.
- Retain aggregate whole-document tokens and normalized content.
- Preserve raw-content fallback when no usable fields exist.
- Keep whole-document metadata term frequencies unchanged.
- Document the package-level artifact model in `package-info.java`.

## Excluded

- Changes to `Posting` or `InvertedIndex`.
- Field-specific query syntax.
- Field boosts, BM25F, or score explanations.
- Field-aware sparse-vector weighting.

# Validation

- Separate fields retain separate ordered normalized token sequences.
- Blank fields are ignored.
- Raw-content fallback remains intact.
- Aggregate tokens remain compatible with IR-1.
- Existing lexical and vector tests remain green.

# Relationships

- Implements the next slice of [ADR-005](../../decisions/adr-005-field-aware-indexing.md).
- Depends on the completed [IR-1 PreprocessedDocument phase](../../logs/core/ir-1-preprocessed-document.md).
- Uses the baseline findings from the [Field Model Readiness Review](../../reviews/core/field-model-indexing-readiness.md).
