# Task — IR-5: Materialize Implementation Engineering Plan

## Role

Act as **Clio under the OSK Engineering Role**.

IR-5 Score Explanation has completed:

* objective selection;
* architectural design;
* adversarial review;
* R2 reconciliation;
* canonical use-case definition;
* test/evidence derivation;
* documentation hygiene.

Planning and behavioral design are considered complete.

Your task is **not to redesign IR-5**.

Your task is to materialize the approved work into the canonical implementation plan that agents will execute slice by slice.

Do not modify production code.

---

# Authoritative Inputs

Read the current repository versions of:

* canonical IR-5 use cases and test traceability;
* `docs/engineering/agents/reports/ir-5-score-explanation-plan-r2.md`;
* Brio's adversarial review;
* relevant current architecture / CKF documentation;
* applicable ADRs;
* `AGENTS.md` / engineering-role instructions.

Repository state wins over paths or wording in older reports.

---

# Deliverable

Create:

`docs/engineering/plans/engineering_plan.md`

If `docs/engineering/plans/` does not exist, create it.

This file becomes the **execution plan for IR-5 implementation**.

Do not duplicate the entire R2 report.

Translate the approved design into executable engineering slices with explicit ownership, dependencies, evidence, gates, and completion criteria.

---

# Planning Principle

The implementation flow should follow:

```text
Canonical Use Cases
        ↓
Engineering Slice
        ↓
Implementation + Tests
        ↓
Evidence
        ↓
Required Review Gate
        ↓
Next Slice
```

Each implementation slice must be independently understandable by an Engineering Role agent without requiring it to rediscover the IR-5 design.

However, the plan should reference canonical artifacts rather than copying them unnecessarily.

---

# Roles

Use explicit OSK role ownership.

## Engineering Role

Owns implementation slices.

Responsibilities include:

* production code;
* unit/formula/invariant/integration tests belonging to the core library;
* build validation;
* regression validation;
* engineering report/evidence required by the slice.

Do not create a separate QA slice merely because a slice introduces tests.

For IR-5, tests proving ranking formulas, invariants, API semantics, and core integration behavior are part of engineering implementation.

---

## Architecture Reviewer

Create an Architecture Review slice when the accumulated implementation materially changes or introduces architectural contracts/boundaries.

For IR-5, the significant changes include:

```text
Ranker
   ↓
evaluate(...)
   ↓
TermScoring
   ↓
score() projection
```

and:

```text
Searcher
    ↑
ExplainableSearcher
    ↑
SimpleSearcher
```

The Architecture Reviewer should inspect the **implemented architecture**, not redesign it.

Prefer one architecture-review gate after the architectural implementation is complete rather than ceremony after every individual change.

---

## Engineering Reviewer

After implementation and architectural verification, include a final Engineering Review slice.

Its responsibility is to verify:

* implementation against canonical use cases;
* test/evidence completeness;
* regression safety;
* scope discipline;
* unresolved findings;
* implementation quality.

The Engineering Reviewer must not simply repeat the Architecture Review.

---

# QA Boundary

Do **not** add a QA Engineer slice to IR-5 unless repository evidence reveals a system-level validation requirement not covered by engineering tests.

Current expectation:

> IR-5 is a core/backend library capability whose required evidence is formula, unit, invariant, integration, and regression testing inside the engineering boundary.

A QA slice would become appropriate for capabilities requiring external/system validation such as:

* API behavior across deployed components;
* database-backed integration workflows;
* browser/E2E behavior;
* cross-service workflows;
* externally observable user journeys;
* environment-specific acceptance testing.

IR-5 currently has none of those.

Document this decision briefly in the plan.

---

# Required Execution Slices

Derive the exact plan from the canonical R2 plan and use cases.

The expected structure is approximately the following.

---

## IR-5.1 — Explanation Domain Model

**Owner:** Engineering Role

Implement the approved explanation model.

Expected capability includes:

* `TermScoring`;
* `BinaryTermScoring`;
* `TfIdfTermScoring`;
* `Bm25TermScoring`;
* `FieldBoost`;
* `ScoreExplanation`.

No ranker/searcher wiring yet.

The slice must identify:

* canonical UCs advanced;
* canonical tests/evidence;
* files/packages affected;
* acceptance criteria;
* validation commands;
* required engineering report;
* explicit non-goals.

This must remain an additive, independently green slice.

---

## IR-5.2 — Ranker Evaluation Contract and Atomic Migration

**Owner:** Engineering Role

Implement the approved:

```text
evaluate(...)
     ↓
TermScoring
     ↓
contribution()
```

architecture.

Migrate all existing lexical rankers atomically:

* Binary;
* TF-IDF;
* BM25.

Implement field-aware scoring evidence without changing existing score semantics.

This slice must explicitly protect:

```text
evaluate(...).contribution()
==
score(...)
```

and existing search behavior.

All rankers must migrate in the same slice so the repository never lands with an incomplete `Ranker` contract migration.

Do not introduce BM25 tuning/configurability.

---

## IR-5.3 — Explainable Search Capability

**Owner:** Engineering Role

Implement:

```text
ExplainableSearcher extends Searcher
```

and the approved `SimpleSearcher` explanation behavior.

Include the shared query-analysis primitive so:

```text
searchDetailed(query)
        │
        ├──── analyzeQuery(query)
        │
explain(query, documentId)
```

cannot drift.

`VectorSearcher` must remain outside the capability.

This slice should complete the functional implementation of UC-1 through UC-6.

---

# Architecture Review Gate

## IR-5.AR — Score Explanation Architecture Review

**Owner:** Architecture Reviewer

**Depends on:** IR-5.1, IR-5.2, IR-5.3

Do not implement production changes in this slice.

Review the implemented IR-5 architecture against:

* canonical use cases;
* approved R2 architecture;
* applicable ADRs;
* existing core architecture boundaries.

At minimum verify:

### Ranking boundary

```text
Ranker.evaluate(...)
        ↓
TermScoring
        ↓
score() projection
```

remains a single scoring source of truth.

### Explainability capability

Verify:

```text
Searcher
    ↑
ExplainableSearcher
    ↑
SimpleSearcher
```

represents capability correctly and that `VectorSearcher` was not polluted with unsupported semantics.

### Extensibility

Verify `TermScoring` remains suitable for the research-platform character of myIR and does not accidentally recreate a closed-world hierarchy.

### Domain boundaries

Verify score explanation remains in the appropriate ranking/search boundaries and does not leak into:

* indexing;
* corpus;
* vector retrieval;
* evaluation;
* web/application layers.

### Scope

Confirm implementation did not introduce:

* rank-position explanation;
* BM25 tuning;
* vector explanation;
* IR-6 evaluation concepts;
* rank fusion.

### Review output

Produce the standard Architecture Review artifact according to OSK conventions.

Verdict must clearly indicate whether implementation may proceed to completion or requires remediation.

If findings require production changes, create/remediate those changes under an **Engineering Role remediation slice**, not under the Architecture Reviewer role.

---

# Architecture Review Remediation

The plan must define the conditional flow:

```text
IR-5.AR
   │
   ├── PASS
   │      ↓
   │    IR-5.4
   │
   └── findings requiring code changes
          ↓
       IR-5.AR-F
       Engineering Role
          ↓
       Architecture re-verification
```

Do not create `IR-5.AR-F` as mandatory work if no findings exist.

Represent it as a conditional slice/gate.

---

## IR-5.4 — Knowledge and Usage Documentation

**Owner:** Engineering Role

**Depends on:** successful Architecture Review.

Complete the approved CKF/README documentation.

Document the behavior actually delivered, not merely the planned architecture.

Update canonical knowledge according to repository CKF conventions.

Ensure the canonical use cases remain linked to the implementation evidence.

No production behavior should be introduced in this slice.

---

# Final Engineering Review

## IR-5.R — IR-5 Engineering Review

**Owner:** Engineering Reviewer

**Depends on:** IR-5.1 through IR-5.4 and successful Architecture Review.

Review the completed IR-5 implementation against the canonical behavioral contract.

At minimum verify:

* UC-1 through UC-6;
* required T-01 through T-30 evidence;
* formula correctness;
* score conservation;
* field-aware explanation behavior;
* search regression compatibility;
* capability semantics;
* query-analysis parity;
* documentation completeness;
* scope discipline;
* build/test status.

Do not redesign IR-5.

Do not implement fixes while acting as reviewer.

Produce the standard Engineering Review artifact with findings and verdict.

If remediation is required:

```text
IR-5.R
   ↓
IR-5.R-F       Engineering Role
   ↓
IR-5.RV        Engineering Reviewer verification
```

These remediation slices are conditional and should only materialize when findings exist.

---

# Expected Plan Flow

The resulting `engineering_plan.md` should make this lifecycle explicit:

```text
IR-5.1  Engineering
   ↓
IR-5.2  Engineering
   ↓
IR-5.3  Engineering
   ↓
IR-5.AR Architecture Review
   │
   ├─ PASS ───────────────┐
   │                      ↓
   └─ findings → AR-F → reverify
                          ↓
IR-5.4  Engineering / CKF
   ↓
IR-5.R  Engineering Review
   │
   ├─ PASS → IR-5 COMPLETE
   │
   └─ findings → R-F → RV → COMPLETE
```

---

# Slice Schema

Every mandatory slice in `engineering_plan.md` must contain:

* **ID**
* **Name**
* **Owner / OSK Role**
* **Purpose**
* **Depends On**
* **Canonical Use Cases**
* **Canonical Test Evidence**
* **Implementation Scope**
* **Expected Files / Areas**
* **Acceptance Criteria**
* **Validation**
* **Required Artifact / Report**
* **Non-Goals**
* **Exit Gate**
* **Next Slice**

Review slices must additionally contain:

* review questions;
* evidence inputs;
* finding severity policy if defined by the applicable role;
* verdict expectations;
* remediation routing.

---

# Traceability

Do not create another independent test matrix.

Reference the canonical use-case specification containing T-01 through T-30.

The execution plan should make traceability easy to follow:

```text
UC
 ↓
Test Evidence
 ↓
Implementation Slice
 ↓
Engineering Report
 ↓
Review
```

If the canonical use-case document was moved during documentation hygiene, resolve and use its **current repository path**.

Do not preserve stale paths from older reports.

---

# Status Model

The plan should initially mark:

```text
IR-5.1   READY
IR-5.2   BLOCKED BY IR-5.1
IR-5.3   BLOCKED BY IR-5.2
IR-5.AR  BLOCKED BY IR-5.3
IR-5.4   BLOCKED BY IR-5.AR
IR-5.R   BLOCKED BY IR-5.4
```

Conditional remediation slices should not be marked READY.

They exist only as documented routing until a review produces findings.

---

# QA Decision

Include a short section:

## QA Participation Decision

Record that no separate QA Engineer slice is currently planned because IR-5 is a core/backend library capability and its acceptance evidence is owned by Engineering.

State the conditions that would cause this decision to be revisited.

This is a scope/boundary decision, not a statement that QA is unnecessary in general.

---

# Constraints

Do not:

* modify production code;
* implement IR-5.1;
* redesign approved architecture;
* create new use cases;
* create new tests unless a traceability defect is discovered;
* change IR-6;
* introduce QA work without evidence;
* perform the Architecture Review now;
* perform the Engineering Review now.

This task only materializes the execution plan.

---

# Required Final Response

Report:

1. path of `engineering_plan.md`;
2. mandatory slice count;
3. conditional remediation paths defined;
4. roles assigned to each slice;
5. architecture-review placement and rationale;
6. QA participation decision;
7. confirmation that no production code changed;
8. any inconsistency discovered between canonical use cases and R2.

---

# Definition of Done

This task is complete when:

* `docs/engineering/plans/engineering_plan.md` exists;
* every IR-5 implementation slice has explicit Engineering Role ownership;
* Architecture Review exists as an explicit gate after architectural implementation;
* architecture-review remediation is routed back to Engineering;
* final Engineering Review exists;
* engineering-review remediation and verification are defined conditionally;
* QA participation decision is documented;
* every implementation slice traces to canonical UCs/tests;
* no duplicate behavioral/test specification was created;
* no production code was changed.
