---
type: Engineering Log Entry
title: IR-4 — Field-Aware Ranking / Boosting
description: Completion record for per-field score boosting via RankingContext and FieldWeights, using IR-3 posting field frequencies.
tags: [core, ranking, fields, boosting, ir-4]
timestamp: 2026-07-05T00:00:00Z
ckf_version: "0.1"
ckf_status: completed
ckf_scope: core
ckf_owner: project
---

# Summary

Introduced `FieldWeights` and `RankingContext` to apply configurable field boost
multipliers during ranking. The boost is computed as a frequency-weighted average
across all fields where the query term appears, consuming the `fieldFrequencies`
map added to `Posting` in IR-3. A neutral context and raw-content documents both
short-circuit to the unmodified base score, preserving full backward compatibility.

# Scope

## Included

- New `FieldWeights` record (`codex.ir.ranking`): maps field names to positive
  boost multipliers; `weightFor` defaults unknown fields to `1.0`; `neutral()`
  produces an all-ones instance.
- New `RankingContext` record (`codex.ir.ranking`): wraps `FieldWeights`;
  `neutral()` and `of(FieldWeights)` static factories.
- `Ranker.score(term, posting, RankingContext)` default method: applies
  frequency-weighted boost factor `Σ(fieldFreq[f] × weight[f]) / Σ(fieldFreq[f])`;
  short-circuits to base score when context is neutral or `fieldFrequencies` is empty.
- `SimpleSearcher` gains a `RankingContext` field and a second constructor accepting
  it; the existing constructor delegates to the new one with `RankingContext.neutral()`.
- `Searchers.lexical(..., RankingContext)` factory overload.
- `ranking/package-info.java` updated.
- New `FieldAwareRankingTest` with 10 tests.

## Excluded

- BM25F (field-specific frequencies and length normalization).
- Field-specific query syntax.
- Score explanations.
- Per-field positions.
- Vector search changes.
- Changes to `Rankers` concrete implementations (boost is in the default interface method).

# Validation

`mvn test -pl codex-ir-core` — 197 tests, 0 failures, 0 errors.

IR-4 tests (10 cases):
- `neutral()` FieldWeights returns 1.0 for all fields and unknown fields.
- Explicit weights return configured values; unknown fields default to 1.0.
- Neutral context gives identical score to base `score(term, posting)`.
- Neutral context gives identical ranking to whole-document baseline searcher.
- Title boost with weight 3.0 ranks title-only match above body-only match (TF-IDF).
- Title boost increases TF-IDF score proportionally.
- Title boost increases BM25 score proportionally.
- Frequency-weighted average boost factor is correct for mixed-field documents.
- Raw-content document (empty `fieldFrequencies`) is unaffected by any context.
- Term in unknown field uses weight 1.0 and leaves score unchanged.

All prior 187 tests remain green.

# Decisions

- Boost logic lives in `Ranker` as a `default` method rather than in `SimpleSearcher`
  or a separate utility. This means any `Ranker` implementation (including custom ones)
  gets field-aware scoring for free without modification.
- The boost formula is a frequency-weighted average, not a max or a simple multiplier.
  This means a term appearing equally in title (weight 3.0) and body (weight 1.0) gets
  boost factor 2.0, not 3.0. The relative field contribution is proportional to how
  frequently the term appeared in each field.
- When `base == 0.0` the method returns `0.0` without computing the boost. This avoids
  multiplying a zero score (e.g. IDF=0 for a term that appears in every document).

# Tradeoffs

- Implementing boost in the `default` method means concrete rankers cannot easily
  override it without duplicating the boost formula. If a ranker-specific boost
  formula is needed in the future, the method should be made non-default or a hook
  provided.
- The frequency-weighted average is intuitive but differs from a multiplicative
  field boost (e.g. Lucene's `PerFieldSimilarityWrapper`). The two diverge when
  a term appears with different frequencies across fields.

# Known Limitations

- BM25F (field-specific length normalization) is not implemented. Field-boosted BM25
  is available, but it applies the boost post-hoc rather than integrating field
  statistics into the BM25 formula.
- `RankingContext` is not threaded through vector search.
- Score explanations showing the applied boost per field are not implemented.

# Follow-up

At the time this phase completed, IR-5 Score Explanation was identified as the
next roadmap sequence item: it would expose per-term and per-field contribution
breakdowns so callers can understand why a document ranked where it did.
It is now an unselected candidate, not an automatic next iteration; see the
[current roadmap](../../../roadmap/ROADMAP.md).

# Related Knowledge

- Implements the next slice of [ADR-005](../../decisions/adr-005-field-aware-indexing.md).
- Consumes `Posting.fieldFrequencies()` introduced in [IR-3](ir-3-field-aware-postings.md).
- Builds on [IR-2 Field Provenance](../../phases/core/ir-2-field-provenance.md) and
  [IR-1 PreprocessedDocument](ir-1-preprocessed-document.md).
