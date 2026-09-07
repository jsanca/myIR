# Task — IR-5 Score Explanation: Implementation Use Cases and Engineering Plan Reconciliation

## Role

Act as **Clio — Software Engineer / Engineering Planner**.

IR-5 Score Explanation already has:

* an R1 engineering plan;
* an adversarial review by Brio;
* a reconciled R2 engineering plan.

The current plan is:

`docs/engineering/agents/reports/ir-5-score-explanation-plan-r2.md`

The adversarial review is:

`docs/engineering/agents/reviews/ir-5-score-explanation-adversarial-review.md`

Do not redesign IR-5.

Do not implement production code.

Your task is to formalize the **actual implementation use cases** that IR-5 will deliver and reconcile the R2 engineering plan so that every implementation slice and test can be traced to those use cases.

---

# Objective

Before implementation starts, answer explicitly:

> What observable behaviors are we implementing in IR-5?

The use cases must become the behavioral contract from which:

```text
Use Cases
    ↓
Acceptance Behavior
    ↓
Test Evidence
    ↓
Engineering Slices
    ↓
Implementation
```

are derived.

Architecture must serve the use cases, not substitute for them.

---

# Scope

IR-5 answers:

> **Why did this document receive this score for this query?**

IR-5 does not explain relative rank.

IR-5 does not evaluate whether the ranking is good.

IR-5 does not implement new ranking behavior.

---

# Required Use Cases

Validate these against the current repository and R2 architecture.

Modify wording or split/merge them if repository evidence gives a compelling reason, but preserve their intent.

## UC-1 — Explain the score of a matching document

### Actor

Developer / IR researcher using myIR.

### Given

* a lexical `ExplainableSearcher`;
* a query;
* a document that matches at least one analyzed query term;
* the same ranker, snapshots, and `RankingContext` used for search.

### When

The caller requests an explanation for that query and document.

### Then

myIR returns a `ScoreExplanation` containing the scoring contributions that produced the document score.

The explanation must identify:

* the document;
* the analyzed matched terms;
* the contribution produced for every matched term;
* the total explained score.

### Acceptance behavior

For a result returned by normal search:

```text
explained score ≈ SearchResult.score
```

within the numerical policy established by IR-5.

The explanation must not contain contributions for query terms that did not match the document.

---

## UC-2 — Explain ranker-specific term scoring

### Actor

Developer / IR researcher inspecting lexical ranking behavior.

### Given

A matching `(query term, posting)` scored by one of the currently supported rankers:

* Binary;
* TF-IDF;
* BM25.

### When

The term contribution is evaluated.

### Then

myIR exposes the scoring evidence meaningful to that ranker.

### Binary

Expose only information meaningful to binary ranking.

At minimum:

```text
term
base contribution
final contribution
```

Do not invent TF/IDF-like statistics for binary scoring.

### TF-IDF

Expose the useful scoring components necessary to understand the contribution.

Expected candidates:

```text
term
term frequency
sublinear TF
IDF
base contribution
final contribution
```

### BM25

Expose the useful components necessary to understand:

* term-frequency saturation;
* document-length normalization;
* IDF;
* final contribution.

Expected candidates:

```text
term
tf
idf
document length
average document length
k1
b
normalization factor
base contribution
final contribution
```

Do not expose formula intermediates that add no explanatory value.

### Acceptance behavior

The contribution exposed by each scoring representation must be the same contribution used by normal scoring.

There must not be an independently maintained explanation formula.

---

## UC-3 — Explain field-aware scoring effects

### Actor

Developer / IR researcher investigating why two otherwise similar term matches received different scores.

### Given

A posting with field provenance and a non-neutral `RankingContext`.

### When

A term is scored.

### Then

the explanation makes it possible to understand:

```text
base contribution
      ×
field-aware boost
      =
final contribution
```

The explanation should show only the information needed to understand how the multiplier was derived.

Expected evidence includes:

* field occurrence frequencies;
* effective weight used for each participating field;
* effective boost factor.

If useful and already naturally produced by the calculation, weighted total / total frequency may also be exposed.

### Required scenarios

The use case must distinguish:

* title-only occurrence;
* body-only occurrence;
* occurrence in multiple fields;
* unknown field using default weight;
* neutral context;
* document without field provenance.

### Acceptance behavior

Neutral context or absent field provenance must not change the base contribution.

Unknown fields must make their effective default behavior observable rather than silently hiding it.

---

## UC-4 — Explanation must not change search semantics

### Actor

Existing caller of myIR search APIs.

### Given

Existing code using regular lexical search.

### When

IR-5 is introduced but no explanation is requested.

### Then

the caller continues to receive the same:

* result set;
* ordering;
* score semantics;
* `SearchResult` shape.

`SearchResult` must remain unchanged.

### Important architectural trade-off

R2 intentionally accepts that:

```text
score(...)
    -> evaluate(...)
    -> TermScoring
    -> contribution()
```

allocates scoring representations on the normal scoring path.

This is acceptable for IR-5 because it structurally guarantees one scoring source of truth.

Do not claim zero allocation overhead.

The invariant here is **behavioral compatibility**, not zero runtime overhead.

---

## UC-5 — Explanation support is an explicit capability

### Actor

Caller selecting a search capability.

### Given

myIR has different search implementations.

### Then

support for score explanation must be structurally explicit.

Expected design:

```text
Searcher
    ↑
ExplainableSearcher
```

or the equivalent architecture validated in R2.

### Acceptance behavior

A caller must not have to interpret:

```text
Optional.empty()
```

as either:

```text
document/query produced no explanation
```

or:

```text
this search implementation does not support explanation
```

These states must remain semantically distinct.

`SimpleSearcher` supports IR-5 lexical explanation.

`VectorSearcher` does not gain explanation capability in IR-5.

---

## UC-6 — Explanation uses the same analyzed query semantics as search

### Actor

Developer / researcher expecting explanation to describe the search that actually occurred.

### Given

A query processed by `SimpleSearcher`.

### When

The query is searched and later explained.

### Then

both operations use the same:

```text
tokenization
normalization
normalized-term semantics
```

Explanation must not introduce a shadow query-analysis pipeline.

### Required scenarios

Include:

* normal multi-term query;
* terms removed/changed by normalization if current pipeline supports that behavior;
* duplicate query terms;
* query whose analyzed representation is empty.

### Acceptance behavior

For the same query and searcher:

```text
analyzedTerms(search)
==
analyzedTerms(explain)
```

according to current search semantics.

Do not change those semantics as part of IR-5.

If duplicate terms currently influence scoring, explanation must faithfully expose that behavior rather than silently correcting it.

---

# Explicit Non-Use-Cases

Document these as out of scope.

## NU-1 — Explain relative ranking

IR-5 does not answer:

> Why is document A rank 2 instead of rank 3?

That requires comparison against competing results.

IR-5 explains the score of one document.

---

## NU-2 — Evaluate retrieval quality

No:

* nDCG;
* DCG;
* MAP;
* MRR;
* qrels;
* experiment runner.

Those belong to IR-6.

---

## NU-3 — Explain vector similarity

No sparse/dense vector explanation in IR-5.

`VectorSearcher` remains unchanged.

---

## NU-4 — Explain rank fusion

No:

* RRF;
* lexical/vector fusion;
* signal normalization;
* fused score explanation.

Future work only.

---

## NU-5 — Tune BM25

Do not expose new BM25 configuration APIs.

Existing `k1` and `b` may appear as explanatory evidence because they influence the score.

Changing them is outside IR-5.

---

# Use Case Format

For every final use case include:

```text
ID
Name
Actor
Intent
Preconditions
Trigger
Main Flow
Alternative / Edge Flows
Observable Result
Acceptance Evidence
Non-Goals
```

Avoid UI-oriented use cases.

These are library/research-platform use cases.

---

# Test Derivation

After finalizing the use cases, derive the test matrix from them.

Each test must identify which use case and behavior it proves.

Use a traceability format such as:

| Test | Use Case | Behavior            | Level   |
| ---- | -------- | ------------------- | ------- |
| T1   | UC-2     | Binary contribution | Formula |
| T2   | UC-2     | TF-IDF formula      | Formula |
| T3   | UC-3     | Title field boost   | Formula |
| ...  | ...      | ...                 | ...     |

Do not preserve R2's T1–T24 numbering merely for historical reasons.

Preserve tests that prove useful behavior.

Remove tests that only exist because an earlier design happened to contain a particular type.

Tests should prioritize:

1. formula correctness;
2. score/explanation parity;
3. field behavior;
4. query-analysis parity;
5. capability semantics;
6. regression compatibility.

---

# Required Evidence Levels

Classify tests where useful as:

```text
formula
unit
integration
invariant
regression
```

For formula tests, expected results must be independently derived.

Do not calculate the expected value by calling another path through the same implementation.

---

# Reconcile the Engineering Plan

After defining the use cases and tests, revisit:

`docs/engineering/agents/reports/ir-5-score-explanation-plan-r2.md`

Do not rewrite the architecture unless the use-case analysis reveals an actual conflict.

Ensure every engineering slice identifies:

```text
Implements:
  UC-x
  UC-y

Proves:
  T-x
  T-y
```

---

# Remove the no-op slice

R2 currently preserves IR-5.3 as a no-op numbering anchor for historical traceability.

Do not retain an empty implementation slice.

Historical numbering does not justify work that performs no engineering change.

Preserve traceability in documentation instead.

Renumber the active implementation slices if necessary.

A likely final structure may resemble:

```text
IR-5.1
Explanation model

IR-5.2
Atomic Ranker evaluate migration
+ Binary
+ TF-IDF
+ BM25
+ field-aware scoring evidence if cohesive

IR-5.3
ExplainableSearcher
+ shared query analysis
+ explanation reconstruction

IR-5.4
Documentation / examples / CKF
```

Do not force this exact shape if repository evidence suggests a better division.

The important requirement is:

> every slice produces a coherent, testable increment.

---

# Engineering Plan Requirements

For every final implementation slice specify:

## Objective

What capability becomes available?

## Use Cases

Which UCs are implemented or advanced?

## Code Areas

Which packages/classes are expected to change?

## Contract Changes

What API/domain contracts change?

## Test Evidence

Which tests prove completion?

## Acceptance Criteria

Observable conditions required to mark the slice complete.

## Risks

Only risks relevant to this slice.

## Non-Goals

Explicit boundaries.

## Build Integrity

Explain why the repository remains buildable and coherent after this slice independently.

---

# Artifacts

Create:

`docs/engineering/use-cases/ir-5-score-explanation-use-cases.md`

If the repository uses a more appropriate established location for use-case specifications, use it and document why.

Update:

`docs/engineering/agents/reports/ir-5-score-explanation-plan-r2.md`

to incorporate:

* final use-case identifiers;
* test traceability;
* revised engineering slices;
* removal of the no-op slice.

Do not modify Brio's review.

Do not modify production code.

---

# Required Final Report

Report:

1. final use-case count;
2. final use-case IDs/names;
3. test count derived from the use cases;
4. final engineering slice count;
5. any change made to the R2 architecture because of use-case analysis;
6. remaining open questions;
7. confirmation that no production code changed.

---

# Definition of Done

The task is complete when:

* IR-5 has an explicit behavioral use-case specification;
* every use case has observable acceptance evidence;
* non-use-cases clearly bound the iteration;
* the test matrix derives from the use cases;
* every test traces to a behavior;
* every engineering slice traces to one or more use cases;
* every slice has acceptance evidence;
* no empty/no-op engineering slice remains;
* IR-5 remains score explanation only;
* IR-6 remains independent;
* no production code has been changed.
