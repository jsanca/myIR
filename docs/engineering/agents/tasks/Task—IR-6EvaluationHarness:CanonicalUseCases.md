# Task — IR-6 Evaluation Harness: Canonical Use Cases

## Objective

Define the canonical use cases and behavioral contract for IR-6 — Evaluation Harness.

IR-6 must provide a reproducible way to evaluate and compare ranked retrieval results against explicit relevance judgments.

This task is specification-only.

Do not design the implementation architecture and do not modify production code.

## Context

myIR currently supports:

- Binary ranking
- TF-IDF ranking
- BM25 ranking
- field-aware lexical scoring / boosting
- sparse-vector cosine retrieval
- IR-5 Score Explanation

IR-5 answers:

> Why did this document receive this score for this query?

IR-6 must answer:

> How good is the ranking produced for this query, and how does one retrieval configuration compare with another?

The evaluation model should follow the research already captured for IR-6, including the distinction between:

- binary relevance judgments
- graded relevance judgments

and candidate metrics such as:

- Precision / Recall
- MRR
- MAP
- DCG / nDCG

Do not assume that every metric belongs in the first implementation slice. Determine the behavioral use cases first.

## Required Work

### 1. Review existing knowledge

Inspect:

- current myIR architecture documentation
- roadmap / IR-6 references
- prior research concerning Information Retrieval evaluation
- IR-5 use cases and execution artifacts as a structural precedent

Reconcile terminology before defining the new contract.

### 2. Define canonical IR-6 use cases

Create:

`docs/knowledge/use-cases/ir-6-evaluation-harness-use-cases.md`

Each use case should define, where applicable:

- actor
- intent
- preconditions
- input
- relevance judgments
- ranked results
- expected behavior
- invariants
- edge cases
- non-goals

The use cases should collectively establish how myIR evaluates:

- a single query
- multiple queries / an evaluation set
- binary relevance
- graded relevance
- ranking order
- cutoff values such as `@k`
- queries with no relevant documents
- missing / incomplete judgments
- comparison between two retrieval configurations or algorithms
- deterministic / reproducible evaluation results

### 3. Define metric semantics

For each metric selected by the use cases, specify its behavioral meaning and required inputs.

Distinguish clearly between metrics appropriate for:

- binary relevance
- graded relevance
- first-relevant-result behavior
- aggregate evaluation across a query set

Do not merely list formulas. State what question each metric answers.

### 4. Define canonical evidence

Create a test/evidence matrix similar to IR-5.

Assign stable test IDs (`T-01`, `T-02`, ...) covering:

- formula examples with manually verifiable expected values
- edge cases
- invariants
- aggregation behavior
- comparison behavior
- regression/determinism where appropriate

The use-case document must own the canonical expected behavior and test IDs.

### 5. Explicit non-use-cases

Identify what IR-6 does NOT attempt to solve.

At minimum evaluate whether the following belong outside IR-6:

- generating relevance judgments automatically
- learning-to-rank
- tuning ranking parameters automatically
- statistical significance testing
- online/A-B evaluation
- click analytics
- dense embeddings
- rank fusion
- changing retrieval/scoring algorithms
- IR-5 score explanation

Keep future capabilities visible without expanding IR-6 scope.

## Constraints

- Specification only.
- No production code.
- No implementation architecture.
- No Maven/build changes.
- Do not redesign existing rankers or searchers.
- Preserve current retrieval behavior.
- Prefer small, explicit concepts suitable for a research/learning IR platform.
- The future engineering plan must be derivable from these use cases.

## Deliverables

1. `docs/knowledge/use-cases/ir-6-evaluation-harness-use-cases.md`
2. Engineering report describing:
    - sources reviewed
    - decisions made
    - unresolved questions, if any
    - proposed behavioral boundary for IR-6

## Completion Criteria

The task is complete when:

- IR-6 has a clear behavioral contract.
- Binary and graded relevance semantics are explicit.
- Selected metric responsibilities are explicit.
- Edge cases are specified.
- Canonical test IDs exist.
- Non-goals prevent scope expansion.
- There is enough information to create an engineering plan without inventing new product behavior.