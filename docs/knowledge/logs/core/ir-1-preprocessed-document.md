---
type: Engineering Log Entry
title: IR-1 — PreprocessedDocument Token Artifact
description: Completion record for the normalized token artifact and removal of redundant tokenization cycles.
tags: [core, indexing, preprocessing, validation]
timestamp: 2026-07-05T00:00:00Z
ckf_version: "0.1"
ckf_status: completed
ckf_scope: core
ckf_owner: project
---

# Summary

Introduced `PreprocessedDocument`, carrying the enriched `Document` and its
ordered normalized tokens through the indexing pipeline. Lexical indexing now
consumes the token list directly, and weighters use cached metadata term
frequencies when available.

# Scope

## Included

- Added the `PreprocessedDocument` record.
- Changed preprocessing to return the token artifact.
- Replaced `PipelineDocumentResolver` with `PreprocessedDocumentConsumer`.
- Routed tokens directly into positional lexical indexing.
- Used `DocumentMetadata.termFrequencies` in TF and TF-IDF weighters.
- Updated batch indexing to carry preprocessed artifacts.
- Added package documentation and focused tests.

## Excluded

- Per-field token sequences.
- Public `DocumentWeighter` changes.
- Query preprocessing changes.
- Field-aware postings or ranking.

# Validation

The core module completed successfully with 165 tests, including 11 focused
`PreprocessedDocumentTest` cases. Coverage verified token/content consistency,
metadata frequencies, positional postings, lexical and vector search,
structured-field aggregation, raw-content fallback, and stop-word removal.

# Decisions

- `PreprocessedDocument` is public so the next provenance artifact can compose
  it without copying its contract.
- The artifact remains transient pipeline state; corpus and index storage keep
  their existing domain objects.
- Weighters prefer cached metadata but retain tokenizer fallback for query
  documents.

# Tradeoffs

- A legacy `index(Document)` path remains for compatibility, leaving two
  lexical-indexing paths temporarily.
- Already-preprocessed documents reconstruct tokens from normalized content.
  This rare compatibility path retains a small split cost.

# Known Limitations

- Tokens represent the aggregate document only; field boundaries are absent.
- Field-aware postings, boosts, and vector weighting remain deferred.

# Follow-up

The active [IR-2 Field Provenance phase](../../phases/core/ir-2-field-provenance.md)
adds per-field token sequences while preserving this aggregate artifact.

# Related Knowledge

- Guided by [ADR-005](../../decisions/adr-005-field-aware-indexing.md).
- Validated against the [Field Model Readiness Review](../../reviews/core/field-model-indexing-readiness.md).
