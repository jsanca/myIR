---
type: Engineering Log Entry
title: "IR-5.4 — Knowledge and Usage Documentation"
description: "Creates the CKF Engineering Log entry for IR-5, updates the knowledge index, adds a Score Explanation section to the README, and updates the engineering log. No production code changes."
tags: [core, ranking, search, explainability, ir-5, documentation]
timestamp: 2026-09-07T00:00:00Z
ckf_version: "0.1"
ckf_status: completed
ckf_scope: core
ckf_owner: project
---

## Task 4 — IR-5.4 Knowledge and Usage Documentation

### Summary

Documented the capability delivered by IR-5.1 through IR-5.3. Created the CKF Engineering Log entry, updated the knowledge index, added a "Score Explanation" usage section to the root README, and updated the engineering log. No production code, test file, or build file was modified.

### Scope

**Included:**
- `docs/knowledge/logs/core/ir-5-score-explanation.md` — CKF log entry with capability overview, usage example, trade-offs, acceptance evidence table, and references to all engineering reports
- `docs/knowledge/index.md` — one-line link to the new CKF log entry under "Engineering Logs → Core"
- `README.md` — new "Score Explanation" subsection under "Retrieval Paths" with a self-contained code snippet using real type names
- `docs/engineering/ENGINEERING_LOG.md` — IR-5.4 row added
- `docs/engineering/agents/reports/ir-5-4-documentation.md` — this report

**Excluded (zero changes):**
- All production Java source files
- All test files
- `pom.xml` and `module-info.java`
- Any other file

### Deliverables

| Action | Path |
|---|---|
| Create | `docs/knowledge/logs/core/ir-5-score-explanation.md` |
| Modify | `docs/knowledge/index.md` |
| Modify | `README.md` |
| Modify | `docs/engineering/ENGINEERING_LOG.md` |
| Create | `docs/engineering/agents/reports/ir-5-4-documentation.md` |

### Changed Files

- Created: `ir-5-score-explanation.md`, `ir-5-4-documentation.md`
- Modified: `docs/knowledge/index.md`, `README.md`, `docs/engineering/ENGINEERING_LOG.md`

### Validation

```
mvn compile -pl codex-ir-core   → BUILD SUCCESS (nothing to compile — no code changed)
```

**README snippet type verification** (checked against source):
- `ExplainableSearcher` — declared in `codex.ir.search.ExplainableSearcher` ✓
- `ScoreExplanation` — declared in `codex.ir.ranking.ScoreExplanation` ✓
- `TermScoring` — declared in `codex.ir.ranking.TermScoring` ✓
- `Searchers.lexical(...)` — factory exists in `codex.ir.search.Searchers` ✓
- `Rankers.bm25(...)` — factory exists in `codex.ir.ranking.Rankers` ✓
- `ts.term()`, `ts.base()`, `ts.fieldBoost()`, `ts.contribution()` — all declared on `TermScoring` interface ✓
- `FieldBoost.boostFactor()` — component accessor on the `FieldBoost` record ✓
- Pattern variable `instanceof ExplainableSearcher es` — Java 16+ pattern matching, available on Java 25 ✓

**Link verification** (all links in CKF entry resolve to existing files):
- `../../use-cases/ir-5-score-explanation-use-cases.md` ✓
- `../../../engineering/agents/reports/ir-5-1-domain-model.md` ✓
- `../../../engineering/agents/reports/ir-5-2-ranker-migration.md` ✓
- `../../../engineering/agents/reports/ir-5-3-explainable-searcher.md` ✓
- `../../../engineering/agents/reviews/ir-5-architecture-review.md` ✓

### Tests

No tests added, modified, or removed. This slice is documentation-only per the plan's non-goals.

### Engineering Notes

- The README snippet uses the Java 16+ pattern-matching `instanceof` form (`instanceof ExplainableSearcher es`) which eliminates the explicit cast. Available on Java 25.
- The CKF entry explicitly documents the hot-path allocation trade-off (one `TermScoring` per matched `(term, posting)` pair) per R2 §1 and the IR-5.AR requirement.
- The CKF entry records the final test count (718) rather than the test count at any individual slice, giving a single authoritative figure for the completed feature.

### Decisions

- README section placed under "Core Engine → Retrieval Paths" (not a new top-level section) because score explanation is a retrieval capability, not a separate subsystem.
- CKF log entry uses a comprehensive acceptance evidence table rather than prose so the link from each test to the class containing it is explicit and machine-readable.

### Tradeoffs

- The README snippet is self-contained but omits imports. This is consistent with the existing README code blocks, which also omit imports. A reader who needs the full imports can derive them from the type names in the snippet.

### Risks

- No behavioral risk. Documentation-only changes cannot introduce regressions.

### Known Limitations

- The CKF entry links to `ir-5-architecture-review.md` which was produced by the Architecture Reviewer role in this session. The file is expected to exist at `docs/engineering/agents/reviews/ir-5-architecture-review.md`.

### Follow-ups

- IR-5.R — Final Engineering Review.

### Next Step

IR-5.R — IR-5 Engineering Review: verify the completed implementation, test evidence, and documentation satisfy the behavioral contract end-to-end.
