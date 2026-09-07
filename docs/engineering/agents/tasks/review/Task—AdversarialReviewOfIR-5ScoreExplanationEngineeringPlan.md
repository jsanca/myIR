# Task — Adversarial Review of IR-5 Score Explanation Engineering Plan

## Role

Act as **Adversarial Reviewer** through the OSK role and skill context available in TRAE.

This task is intentionally being executed through the TRAE IDE harness to validate that the projected OSK role and review skills influence the review behavior.

Do not redesign IR-5 from scratch.

Your job is to **challenge the existing engineering plan, identify architectural weaknesses, expose hidden assumptions, and recommend only evidence-backed corrections**.

## Primary Artifact

Review:

`docs/engineering/agents/reports/ir-5-score-explanation-plan.md`

Treat this document as the proposed engineering plan under review.

Inspect the actual repository where necessary to validate claims made by the plan.

## Project Context

myIR has completed IR-0 through IR-4.

Relevant current capabilities include:

* lexical retrieval;
* Binary, TF-IDF, and BM25 ranking;
* positional postings;
* field-aware postings;
* field-aware boosting through `RankingContext`;
* sparse vector retrieval;
* snapshot-based reads;
* interface + factory patterns;
* records and explicit domain types;
* strong unit/formula-level test coverage.

IR-5 exists to answer:

> Why did this document receive this score and rank here for this query?

IR-6 will later answer:

> Is configuration A better than configuration B?

IR-5 must not absorb IR-6 responsibilities.

Observability is a core design value of myIR.

## Review stance

Assume the plan is plausible but potentially wrong.

Do not approve merely because:

* the proposed design is internally consistent;
* tests are extensive;
* the plan aligns with the requested scope;
* the code changes appear incremental.

Actively look for:

* incorrect assumptions about existing architecture;
* hidden coupling;
* semantic ambiguity;
* API smells;
* accidental scope expansion;
* performance costs disguised as abstraction;
* premature generalization;
* closed-world assumptions that conflict with a research platform;
* test invariants that do not prove what they claim;
* inconsistencies between stated goals and actual design.

## Mandatory review questions

### 1. Single-source-of-truth vs hot-path allocation

The plan recommends:

```text
evaluate(...) -> TermScoring
score(...) -> evaluate(...).contribution()
```

This structurally prevents score/explanation drift.

However, it also means normal search allocates `TermScoring` objects even when explanation is never requested.

Challenge the plan on this trade-off.

Determine:

* whether the claim that normal search is "unchanged" is accurate;
* whether explanation is truly on-demand;
* whether the allocation cost is acceptable at this stage;
* whether an alternative preserves single-source-of-truth without forcing rich objects into the scoring hot path;
* whether this should be accepted intentionally as a learning-platform trade-off rather than hidden.

Do not optimize speculatively.

We want a clear architectural judgment.

### 2. `sealed TermScoring` as a closed-world model

The plan proposes a sealed hierarchy:

```text
TermScoring
 ├─ BinaryTermScoring
 ├─ TfIdfTermScoring
 └─ Bm25TermScoring
```

myIR is a research and experimentation platform.

Future ranking signals may include:

* phrase/proximity scoring;
* additional lexical models;
* sparse-vector explanations;
* dense-vector explanations;
* fusion contributions;
* experimental rankers.

Challenge whether a sealed hierarchy is consistent with this direction.

Evaluate:

* sealed vs non-sealed interface;
* ranker-specific types vs more compositional explanation components;
* extensibility without degrading type safety;
* whether modifying a `permits` list for every experiment is desirable or artificial friction.

Do not prematurely design future fusion.

Recommend the smallest model that remains honest about future experimentation.

### 3. Capability semantics of `Searcher.explain`

The plan proposes adding:

```text
Searcher.explain(query, documentId)
```

and allowing `VectorSearcher` to inherit a default implementation that returns `Optional.empty()`.

Challenge this carefully.

`Optional.empty()` may mean either:

```text
there is no explanation for this query/document
```

or:

```text
this Searcher implementation does not support explanation
```

These are not necessarily the same state.

Evaluate whether explanation belongs directly on `Searcher`, or whether capability separation is more semantically correct.

Consider, without assuming the answer:

```text
Searcher
ExplainableSearcher
```

or another composition consistent with current myIR architecture.

Inspect existing interface/factory patterns before recommending a change.

### 4. BM25 parameter configurability scope creep

The plan optionally proposes exposing:

```text
Rankers.bm25(corpus, index, k1, b)
```

IR-5 is Score Explanation.

BM25 parameter tuning is potentially valuable for future IR-6 experiments but is not required to explain existing scores.

Determine whether this belongs in IR-5.

Default position should be:

> exclude unrelated capabilities unless there is a direct architectural dependency.

If you believe it belongs, provide evidence.

### 5. Explanation model granularity

Challenge whether the proposed structures are the smallest useful model:

```text
ScoreExplanation
TermContribution
TermScoring
FieldBoost
```

In particular evaluate whether `TermContribution` is merely a wrapper with speculative future value.

The plan itself admits it may be redundant.

Recommend whether to:

* keep it;
* remove it;
* or defer it.

Avoid architecture for hypothetical future fusion unless current design clearly benefits.

### 6. Score-conservation invariant

The plan proposes enforcing:

```text
sum(contributions) == ScoreExplanation.score
```

inside the `ScoreExplanation` constructor.

Challenge:

* whether a domain record should enforce floating-point reconciliation itself;
* whether a fixed tolerance belongs in a domain constructor;
* absolute vs relative tolerance;
* whether ordering/addition behavior can create false failures;
* whether this invariant belongs structurally, in a factory, or primarily in tests.

The invariant itself is important.

Review the proposed enforcement mechanism, not the goal.

### 7. Snapshot consistency

The plan reconstructs an explanation on demand after search.

Validate the assumption that the same:

```text
query
documentId
ranker
RankingContext
snapshots
```

are sufficient to reproduce the exact contribution that produced the original result.

Look for any state, analysis, ordering, or lifecycle detail that could break this assumption.

Challenge the documented behavior around stale search results and snapshot replacement.

### 8. Query analysis duplication

`SimpleSearcher.explain(...)` is proposed to tokenize and normalize the query again.

Inspect whether this would duplicate search orchestration logic.

Determine whether:

* shared query-analysis primitives already exist;
* explanation risks drifting from `searchDetailed`;
* an internal reusable query-analysis step should be extracted;
* or extraction would be unnecessary refactoring.

The same principle applies:

> explanation must observe the scoring pipeline, not create a shadow pipeline.

### 9. Ranker identity

The plan considers:

```text
ranker.getClass().getSimpleName()
```

as `rankerName`.

Challenge whether explanation should contain a ranker identity at all and, if so, whether implementation class name is an appropriate stable identity.

Avoid introducing a naming system unless justified.

### 10. Rank semantics

IR-5 asks:

> Why did this document receive this score and rank here?

The proposed model primarily explains **score**.

Determine whether it actually explains **rank**.

For example:

```text
DOC-A score 4.82 rank 3
```

Knowing why DOC-A scored 4.82 does not necessarily explain why it is rank 3 unless the competing scores are considered.

Determine whether the IR-5 wording should be narrowed to:

> Why did this document receive this score?

or whether rank context belongs in the first slice.

Do not add comparison/fusion tooling just to satisfy wording.

This is primarily a semantic review question.

## Test review

Review the proposed T1–T20 test matrix adversarially.

Identify:

* redundant tests;
* missing tests;
* tests that only repeat implementation details;
* tests whose names promise stronger guarantees than they provide;
* regression assertions that should remain black-box;
* formula tests that should use independently derived expected values.

Pay particular attention to:

* score parity;
* floating-point tolerance;
* duplicate query terms;
* deterministic ordering;
* field-frequency edge cases;
* empty/neutral contexts;
* zero/invalid corpus statistics if reachable;
* behavior when a document exists but does not match the query.

Do not expand the suite without justification.

## Scope review

Verify that IR-5 remains isolated from:

* IR-6 evaluation;
* dense vectors;
* rank fusion;
* persistence;
* crawler/web concerns;
* performance benchmarking;
* telemetry infrastructure;
* BM25 tuning;
* unrelated cleanup.

Call out any opportunistic changes hidden inside a slice.

## Engineering slice review

Review IR-5.1 through IR-5.6.

For each slice determine:

* can it actually leave the build green independently?
* does it introduce a temporarily inconsistent API?
* is the order correct?
* is any slice too large?
* is any slice artificial?
* are documentation changes placed appropriately?

Especially inspect whether migrating Binary/TF-IDF before BM25 leaves the `Ranker` interface in a compilable state.

## Required output

Create an adversarial review report.

Recommended location:

`docs/engineering/agents/reviews/ir-5-score-explanation-adversarial-review.md`

Use this structure:

1. Executive Assessment
2. Verdict
3. Critical Findings
4. Major Findings
5. Minor Findings
6. Mandatory Question Review
7. Test Matrix Review
8. Slice-by-Slice Review
9. Proposed Corrections
10. What Should Explicitly Remain Unchanged
11. Risks Accepted Intentionally
12. Final Recommendation

Use severity where helpful:

```text
BLOCKER
MAJOR
MINOR
NOTE
```

## Verdict vocabulary

Choose exactly one:

```text
APPROVE
APPROVE WITH CHANGES
CHANGES REQUIRED
REJECT
```

Do not use a soft ambiguous verdict.

## Evidence requirements

For every BLOCKER or MAJOR finding:

* cite the relevant section of the engineering plan;
* cite the repository code or architectural evidence that supports the finding;
* explain the failure mode;
* propose the minimum correction.

Separate:

```text
observed fact
architectural inference
recommendation
```

Do not present preferences as facts.

## OSK / TRAE harness observation

Because this task is also an experiment of the TRAE OSK adapter, add a small final section:

### Harness Observation

Report only observable evidence about whether the OSK context affected task execution.

Examples:

* role instructions were discoverable;
* adversarial-review skill instructions were available;
* verification/boundary skills influenced the review;
* expected OSK artifacts were unavailable;
* instructions conflicted or were incomplete.

Do not infer IDE internals that cannot be observed.

This section is diagnostic only and must not influence the architectural verdict.

## Non-goals

Do not:

* implement IR-5;
* edit the engineering plan;
* refactor production code;
* create an alternative full design;
* implement missing adapters;
* change roadmap status;
* select IR-6 work.

The result of this task is a **review**, not a repair.

## Definition of Done

The review is complete when:

* the proposed IR-5 architecture has been challenged against actual repository evidence;
* the four known concerns have been explicitly adjudicated:

    * hot-path allocation;
    * sealed hierarchy;
    * `Searcher.explain` capability semantics;
    * BM25 parameter scope creep;
* additional material risks have been surfaced if present;
* the T1–T20 matrix has been adversarially reviewed;
* each implementation slice has been checked for independent viability;
* a clear verdict has been issued;
* minimum required changes are identified;
* no production code has been changed;
* the TRAE/OSK harness observation is recorded separately.
