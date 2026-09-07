---
name: java-testing-verification
description: Design, add, review, and improve Java test coverage. Use when verifying Java behavior with unit tests, integration tests, contract tests, Testcontainers, regression tests, mutation testing, CI quality gates, or AI-generated code review.
---

# Java Testing Verification

## Workflow

1. Inspect the test framework, build tool, test naming conventions, fixtures, and CI commands.
2. Identify the behavior that must be proven, not just the lines that changed.
3. Prefer fast unit tests for pure logic and focused integration tests for boundaries.
4. Use Testcontainers or real dependencies only when mocks would hide important behavior.
5. Include regression tests for bug fixes and edge cases.
6. Run the smallest useful test command first, then broader verification if shared behavior changed.

## References

- Read `references/unit-tests.md` for JUnit 5 unit test patterns and naming.
- Read `references/integration-tests.md` for slice tests, Testcontainers, and CI commands.

## Quality Checklist

- Tests assert observable behavior.
- Tests do not depend on execution order.
- Fixtures are clear and local.
- Assertions explain the contract.
- AI-generated code is verified before trust.

## Output

After test work, summarize:

- Behavior under test and why it matters
- Test type chosen (unit, slice, integration) and rationale
- Files added or changed
- Commands to run locally and in CI
- Gaps intentionally left uncovered
