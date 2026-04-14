# Coding Identity

This document captures recurring design preferences and architectural fingerprints that guide how code should be written in this project family.

It is not meant to be dogma. It is meant to preserve consistency, clarity, and authorship across iterations, refactors, and AI-assisted implementation.

## Core principles

### 1. Prefer simple, boring components over clever abstractions
Favor components that are easy to read, reason about, and replace. Avoid abstractions that try to solve too many future problems at once.

### 2. Derived technical values belong in metadata, not in the conceptual core
If a value exists to accelerate computation or support implementation details, it should usually live in metadata or a clearly technical companion object, not in the semantic heart of the domain model.

### 3. Choose composition over premature hierarchy
Composition is generally preferred over inheritance or deep type trees. Build small parts that collaborate well instead of introducing complex object hierarchies too early.

### 4. Optimize for API clarity before implementation cleverness
Public APIs should read cleanly and reflect the domain clearly. Internal cleverness is acceptable only if it does not leak confusion into the public surface.

### 5. Prefer an initial assumption plus correction when it reads better than a symmetrical branch
When a flow becomes easier to read by assuming the common case first and correcting only when needed, prefer that over a heavier symmetric `if/else` structure.

### 6. Keep responsibilities sharply separated
Objects and components should do one thing well. If a class starts carrying both domain meaning and technical optimization details, that is usually a signal to split responsibilities.

### 7. Treat naming as part of architecture
Names are not decoration. Good names should clarify intent, preserve domain meaning, and reduce the need for explanation elsewhere.

### 8. Use concurrency deliberately, not decoratively
Concurrency should solve a real problem and should be introduced with clear operational boundaries. Prefer explicit control, consistency, and understandable tradeoffs over clever parallelism.

### 9. Prefer stable, explainable tradeoffs over premature optimization
When choosing between elegance, correctness, speed, and complexity, prefer decisions that are easy to explain and maintain. Optimize only where the benefit is real and visible.

### 10. Favor reusable logic outside orchestration contexts
If logic may be useful outside indexing, UI, crawling, or any other orchestration flow, keep it in a reusable component rather than burying it in a specific pipeline.

### 11. Keep components modest and "dumb" when possible
A good component does not need to be overpowered. Small, focused, unsurprising parts are easier to compose, test, and evolve.

### 12. Preserve the fingerprint of decisions, not just the syntax
The project’s identity is not only in formatting or syntax style, but in recurring design choices: what gets separated, what gets simplified, what is considered noise, and what is considered worth preserving.

## Practical reminders

- If a technical discussion ends in a meaningful architectural decision, capture it in an ADR.
- If an abstraction causes discomfort, investigate why before accepting it.
- If an implementation is correct but still feels semantically wrong, keep refining.
- If a component is hard to explain in simple terms, it is probably carrying too much.
- If a future optimization changes the conceptual model, isolate it carefully.