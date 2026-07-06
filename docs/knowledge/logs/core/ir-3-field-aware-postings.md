---
type: Engineering Log Entry
title: IR-3 — Field-Aware Postings
description: Completion record for per-field term frequency in postings, without altering whole-document ranking or search behavior.
tags: [core, indexing, postings, fields, ir-3]
timestamp: 2026-07-05T00:00:00Z
ckf_version: "0.1"
ckf_status: completed
ckf_scope: core
ckf_owner: project
---

# Summary

Extended `Posting` with a `fieldFrequencies` map that records how many times a
term appeared in each named field of a structured-field document. The
whole-document `termFrequency` and `positions` are unchanged. Raw-content
documents produce postings with an empty `fieldFrequencies` map, preserving full
backward compatibility.

# Scope

## Included

- Added `Map<String, Integer> fieldFrequencies` component to `Posting`.
- Added `Posting.Accumulator.addFieldOccurrence(documentId, fieldName)`.
- Added `InvertedIndex.addFieldOccurrence(term, documentId, fieldName)`.
- Implemented `addFieldOccurrence` in `InvertedIndexes.InMemoryInvertedIndex`.
- Upgraded `LexicalIndexer` to implement `FieldAnalyzedDocumentConsumer`;
  after whole-document insertion it records per-field frequencies from each
  `FieldTokenSequence`.
- Updated `BatchPipelineIndexer.indexAll` to pass the full `FieldAnalyzedDocument`
  to `LexicalIndexer` (previously passed only `PreprocessedDocument`).
- Updated `package-info.java` to document the field-frequency posting model.
- Added `FieldAwarePostingsTest` with 10 focused tests.
- Fixed `RankersTest` direct `Posting` constructor calls to include the new component.

## Excluded

- Field-specific ranking (BM25F, field boosts, score explanations).
- Field-specific query syntax ("title:java").
- Per-field positions (only per-field frequency counts are tracked).
- Changes to `Ranker`, `Searcher`, `VectorIndexer`, `DocumentWeighter`, or corpus types.

# Validation

`mvn test -pl codex-ir-core` — 187 tests, 0 failures, 0 errors.

IR-3 tests (10 cases):
- Single-field document carries field frequency in its posting.
- Two-field document carries correct per-field frequency for a shared term.
- Term appearing twice in the same field has field frequency 2.
- Term absent from a field has no entry in that field's frequency.
- Raw-content document has empty `fieldFrequencies`.
- All-blank-field fallback document has empty `fieldFrequencies`.
- Whole-document `termFrequency` and `positions` are unchanged.
- Field frequencies for one document do not bleed into another document's posting.
- `IndexSnapshot` carries field frequency data.
- `batchLexicalAndVector` indexer propagates field frequencies correctly.

All prior tests (177) remain green, confirming no regression in lexical search,
vector search, ranking, or preprocessing behavior.

# Decisions

- `fieldFrequencies` is an additional record component, not a replacement.
  This preserves the existing public `Posting` shape and all existing callers
  that read `termFrequency` and `positions` work without modification.
- `addFieldOccurrence` on `InMemoryInvertedIndex` silently no-ops when the
  accumulator for a term does not exist. The only callers are `LexicalIndexer`
  (which always inserts whole-document occurrences first), so this guard is
  purely defensive.
- `BatchPipelineIndexer.indexAll` now calls `lexicalIndexer.index(fa)` (the
  `FieldAnalyzedDocumentConsumer` path) instead of `lexicalIndexer.index(fa.base())`.
  This is the minimal change to propagate field sequences through the batch path.

# Tradeoffs

- A new mandatory `addFieldOccurrence` method was added to the `InvertedIndex`
  interface. Any external implementation would need to add this method. The
  project has exactly one implementation, so the cost is zero. A `default`
  no-op could be added if external implementations become a concern.
- Per-field positions are not tracked (only per-field frequency counts). Position
  tracking would require a `Map<String, List<Integer>>` per field per document,
  doubling memory use for field metadata. Frequency counts are sufficient for
  BM25F and field-boost use cases.

# Known Limitations

- Field-specific query syntax is not supported; all search remains whole-document.
- Field frequency data in postings is not used by any ranker or searcher yet —
  it is infrastructure for a future IR-4 phase (field-weighted ranking or BM25F).
- The `Posting` record is now a 4-component type; any JSON serialization or
  external tooling that relies on the old 3-component shape will need updating.

# Follow-up

The next slice is field-weighted ranking or field-specific query support, which
can now consume `Posting.fieldFrequencies()` directly from the index.

# Related Knowledge

- Implements the next slice of [ADR-005](../../decisions/adr-005-field-aware-indexing.md).
- Depends on [IR-2 Field Provenance](../../phases/core/ir-2-field-provenance.md)
  which provided `FieldAnalyzedDocument` and `FieldTokenSequence`.
- Builds on [IR-1 PreprocessedDocument](ir-1-preprocessed-document.md).
