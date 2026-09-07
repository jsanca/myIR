# Task — IR-5 Engineering Plan R2: Reconcile Adversarial Review

## Role

Act as **Clio — Software Engineer / Engineering Planner**.

You previously produced the IR-5 Score Explanation engineering plan.

Brio has now completed an adversarial review and issued:

> **CHANGES REQUIRED**

Your task is to reconcile that review into a corrected **IR-5 Engineering Plan R2**.

This is a planning task.

**Do not implement IR-5.**

---

## Primary artifacts

Original engineering plan:

`docs/engineering/agents/reports/ir-5-score-explanation-plan.md`

Adversarial review:

`docs/engineering/agents/reviews/ir-5-score-explanation-adversarial-review.md`

Read both completely before modifying the plan.

Inspect repository code when necessary to verify that the revised slices are actually implementable.

---

# Objective

Produce a corrected engineering plan that:

1. preserves the architectural ideas that survived adversarial review;
2. resolves all BLOCKER and MAJOR findings;
3. adjudicates MINOR findings explicitly;
4. contains independently buildable implementation slices;
5. is ready for a final reviewer verification before implementation begins.

Do not redesign IR-5 from scratch.

---

# Architectural decisions to preserve unless repository evidence disproves them

The adversarial review explicitly supports the central design:

```text
Ranker.evaluate(...)
        │
        ▼
   TermScoring
        │
        └── contribution()

Ranker.score(...)
        │
        ▼
evaluate(...).contribution()
```

The goal remains:

> scoring and explanation share one computational source of truth.

IR-5 explains:

> **Why did this document receive this score?**

Do not expand IR-5 to explain relative rank among competing documents.

IR-6 remains separate and will answer whether one retrieval configuration performs better than another.

---

# Mandatory corrections

## 1. Remove the false zero-allocation claim

The R1 plan claimed that normal search would pay no explanation-related allocation cost while also proposing:

```text
score(...)
    -> evaluate(...)
    -> TermScoring
    -> contribution()
```

These statements are incompatible.

R2 must state explicitly:

* normal scoring will allocate `TermScoring`;
* explanation objects themselves remain on-demand;
* the allocation is an intentional trade-off;
* myIR currently prioritizes a structurally shared scoring/explanation path over speculative optimization.

Do not claim that the search hot path is unchanged.

Do not optimize this without evidence.

Record the performance implication as an accepted risk that can later be measured.

---

## 2. Remove the sealed `TermScoring` hierarchy

Do not use:

```java
sealed interface TermScoring
```

Use an extensible contract suitable for a research platform.

The revised design should allow future experimental rankers to introduce their own scoring/explanation representations without modifying a central `permits` list.

Preserve type safety where practical.

Do not introduce a generic `Map<String,Object>` explanation bag merely to achieve extensibility.

---

## 3. Separate explanation capability from `Searcher`

Do not add an unsupported default:

```java
Searcher.explain(...) -> Optional.empty()
```

`Optional.empty()` must not ambiguously mean both:

* no explanation exists for this query/document;
* this Searcher implementation does not support explanation.

Evaluate and preferably use a capability contract such as:

```java
interface ExplainableSearcher extends Searcher {
    Optional<ScoreExplanation> explain(
        String query,
        String documentId
    );
}
```

Validate the exact shape against existing myIR interface + factory conventions.

`SimpleSearcher` may implement the capability.

`VectorSearcher` should simply not expose it during IR-5.

Do not add vector explanation.

---

## 4. Move score-conservation enforcement out of the domain constructor

Preserve the invariant:

```text
explained contribution sum ≈ actual search score
```

But do not make `ScoreExplanation` construction fail because of floating-point reconciliation.

R2 should place score conservation primarily in formula/integration tests.

Define a deliberate floating-point comparison strategy appropriate for the current scoring ranges.

Do not over-engineer a numerical framework.

---

## 5. Fix implementation slice atomicity

The previous plan introduced abstract:

```java
Ranker.evaluate(...)
```

while migrating Binary and TF-IDF before BM25.

That cannot leave the build green because BM25 still implements `Ranker`.

R2 must restructure the slices so the public `Ranker` contract and **all existing ranker implementations migrate atomically**.

Prefer the simplest solution.

Do not introduce temporary APIs such as an `evaluate()` default throwing `UnsupportedOperationException` merely to preserve an artificial slice boundary.

---

## 6. Remove BM25 configurability from IR-5

Do not add:

```java
Rankers.bm25(..., k1, b)
```

BM25 parameter exposure is potentially valuable for future IR-6 experimentation, but it is not necessary for Score Explanation.

IR-5 may expose the parameters actually used by BM25 as explanation data if that is meaningful.

It must not make them newly configurable.

Record BM25 configurability as a future candidate if appropriate.

---

# Findings to adjudicate

The following recommendations should be evaluated and either accepted or rejected with explicit reasoning.

## `TermContribution`

The original plan proposed:

```text
ScoreExplanation
    -> List<TermContribution>
        -> TermScoring
```

Brio considers `TermContribution` a speculative wrapper.

Determine whether R2 can simply use:

```text
ScoreExplanation
    -> List<TermScoring>
```

Prefer fewer abstractions unless `TermContribution` provides present-day semantics.

Do not keep it solely because future fusion might want it.

---

## Ranker identity

The original plan proposed:

```java
ranker.getClass().getSimpleName()
```

Brio identified this as unstable and unnecessary.

Determine whether `ScoreExplanation` needs ranker identity at all in IR-5.

If not, remove it.

Do not invent a ranker naming subsystem for this iteration.

---

## Query analysis reuse

The explanation path must not become a shadow query-processing pipeline.

Inspect the existing `SimpleSearcher` query flow.

If both search and explanation need:

```text
tokenize
   ↓
normalize
   ↓
normalized query terms
```

extract or reuse the smallest internal primitive necessary so both paths consume the same query analysis behavior.

Do not introduce a public query-analysis abstraction unless justified.

---

# Score vs rank semantics

Correct the IR-5 problem statement throughout R2.

Prefer:

> **Why did this document receive this score for this query?**

rather than:

> Why did this document receive this score and rank here?

IR-5 explains the components that produced a score.

Relative ranking depends on competing documents and is outside this iteration.

Do not expand scope to satisfy the old wording.

---

# Revised conceptual model

Revisit the original proposed types:

```text
ScoreExplanation
TermContribution
TermScoring
BinaryTermScoring
TfIdfTermScoring
Bm25TermScoring
FieldBoost
```

Produce the **smallest model justified by IR-5's current use cases**.

For every surviving type state:

* responsibility;
* package;
* public/internal visibility;
* why it exists now;
* relationship to existing ranking/search types.

Prefer current requirements over speculative fusion requirements.

---

# Field-aware explanation

Preserve the useful part of the original design.

A researcher should be able to understand:

```text
base score
    ×
effective field boost
    =
final contribution
```

For a field-aware contribution, determine the minimal useful evidence among:

* field frequencies;
* configured/effective field weights;
* weighted sum;
* total frequency;
* effective multiplier.

The explanation should make unknown-field/default-weight behavior observable.

Do not expose meaningless arithmetic intermediates simply because they exist.

---

# Revised invariants

R2 should explicitly define and test at least:

### Scoring parity

```text
score(term, posting, context)
≈
evaluate(term, posting, context).contribution()
```

### Explanation conservation

```text
SearchResult.score
≈
sum(ScoreExplanation term contributions)
```

### Neutral context

Field-aware explanation must not alter scores when context is neutral.

### Query-analysis parity

Search and explanation must use identical normalized query terms.

### Determinism

Same snapshot + ranker + context + query + document should produce the same explanation.

### Behavioral compatibility

Existing result ordering and score semantics must remain unchanged.

State whether equality is expected to be bit-identical or tolerance-based for each invariant. Do not promise stronger numerical identity than the implementation can guarantee.

---

# Test matrix revision

Review T1–T20 from R1.

Do not mechanically preserve the count.

Remove tests tied to discarded design elements.

Add tests only where they prove an architectural or scoring property.

Pay particular attention to:

* Binary explanation;
* TF-IDF formula;
* BM25 formula;
* multi-term score conservation;
* field boost;
* multiple fields;
* unknown/default field weight;
* neutral context;
* raw-content documents;
* duplicate query terms if meaningful in current search semantics;
* document exists but query does not match it;
* unsupported explanation capability being represented structurally rather than by `Optional.empty()`;
* deterministic explanation;
* query-analysis parity;
* existing ranking regression.

Formula-level expected values should be independently derived rather than produced by the implementation under test.

---

# Revised engineering slices

Produce a new slice sequence.

Every slice must:

* compile;
* pass all existing tests;
* leave the repository in a coherent state;
* not depend on an interface implementation scheduled for a later slice.

A likely shape may be:

```text
IR-5.1
Explanation domain model

IR-5.2
Ranker evaluate contract
+ Binary
+ TF-IDF
+ BM25
atomic migration

IR-5.3
Field-aware explanation

IR-5.4
ExplainableSearcher
+ shared query analysis
+ explanation integration

IR-5.5
Documentation / CKF
```

This is a suggestion, not a required structure.

Derive the final slices from repository evidence.

For every slice include:

* objective;
* affected code;
* API changes;
* tests;
* acceptance criteria;
* risks;
* non-goals.

---

# Scope boundaries

IR-5 R2 must explicitly exclude:

* IR-6 metrics;
* qrels;
* TREC export;
* experiment runners;
* BM25 tuning/configurability;
* vector explanation;
* dense embeddings;
* proximity scoring;
* rank fusion;
* persistence;
* telemetry backend;
* GUI visualization;
* crawler/web changes;
* unrelated cleanup.

Do not opportunistically fix unrelated findings.

---

# Review traceability

Add a section:

## Adversarial Review Reconciliation

For every finding from:

`docs/engineering/agents/reviews/ir-5-score-explanation-adversarial-review.md`

record:

```text
Finding
Decision: ACCEPTED | PARTIALLY ACCEPTED | REJECTED
Rationale
R2 change
```

At minimum cover:

* build-breaking slice order;
* hot-path allocation;
* Searcher capability conflation;
* constructor invariant;
* sealed hierarchy;
* query-analysis duplication;
* ranker identity;
* BM25 configurability;
* TermContribution;
* score-vs-rank wording;
* test matrix concerns.

A rejection is allowed.

It must be supported by repository or architectural evidence.

Do not treat reviewer recommendations as commands.

---

# Deliverable

Update:

`docs/engineering/agents/reports/ir-5-score-explanation-plan.md`

to **R2**.

Preserve relevant historical context where useful, but the document should represent the current proposed plan rather than containing two competing designs.

Clearly identify it as revision 2 and record that it reconciles Brio's adversarial review.

Do not modify Brio's review.

Do not modify production code.

---

# Required final response

When finished, report:

1. R2 plan location;
2. number of Brio findings accepted / partially accepted / rejected;
3. final number of engineering slices;
4. any remaining open architectural questions;
5. confirmation that no production code was changed;
6. validation performed on the plan/repository.

Do not begin implementation.

---

# Definition of Done

This task is complete when:

* every BLOCKER and MAJOR finding has been resolved or explicitly rejected with evidence;
* the hot-path allocation trade-off is stated accurately;
* `TermScoring` is no longer closed-world unless compelling evidence justifies otherwise;
* explanation capability has unambiguous semantics;
* score conservation remains a tested invariant without unsafe domain-constructor enforcement;
* all existing rankers migrate atomically with the `Ranker` contract;
* BM25 configurability is outside IR-5;
* query analysis cannot silently diverge between search and explanation;
* IR-5 promises score explanation, not relative-rank explanation;
* implementation slices are independently green by design;
* the adversarial review is fully traceable into R2;
* no production implementation has begun.
