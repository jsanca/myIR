---
name: java-core-engineering
description: Apply modern Java engineering practices for idiomatic implementation, maintainability, correctness, and code review. Use when writing, reviewing, or refactoring Java code that is not primarily about a framework, migration, or production diagnostic.
---

# Java Core Engineering

## Workflow

1. Inspect the Java version, build tool, package structure, tests, and local style.
2. Prefer the repository's existing idioms before introducing new patterns.
3. Keep types, names, exceptions, null handling, and immutability explicit.
4. Use collections, streams, records, sealed types, and Optional only where they make code clearer.
5. Preserve behavior while refactoring, then add or update focused tests.
6. Verify with the narrowest useful build or test command.

## References

- Read `references/idioms.md` for records, sealed types, Optional, exceptions, and stream patterns.
- Read `references/refactoring.md` for before/after examples and review guidance.

## Quality Checklist

- Public APIs have clear names and stable contracts.
- Exceptions are intentional and not swallowed.
- Mutability is limited and visible.
- Generics improve type safety without obscuring intent.
- Stream pipelines stay readable.
- Tests cover important behavior and edge cases.

## Output

After changes, summarize:

- Files changed and why
- Behavior preserved or intentionally changed
- Tests added or updated
- Build or test command to run
- Residual risks or follow-ups
