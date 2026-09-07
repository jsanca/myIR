---
name: java-quality-gates
description: Configure and fix Java CI quality gates for Gradle or Maven projects. Use when setting up or repairing check, verify, Checkstyle, SpotBugs, PMD, JaCoCo coverage thresholds, Spotless formatting, enforcer rules, or PR pipeline failures on static analysis and tests.
---

# Java Quality Gates

## Workflow

1. Read the build file (`build.gradle.kts`, `build.gradle`, `pom.xml`) and existing CI workflow — identify what already runs on PR vs main.
2. Map the **canonical gate command** (`./gradlew check`, `./mvn verify`, or project-specific `quality` task).
3. Classify failures: compile, test, static analysis, formatting, coverage, enforcer — fix in that order.
4. Read `references/ci-pipeline-gates.md` when designing or changing pipeline stages.
5. Read `references/static-analysis-and-coverage.md` when tuning Checkstyle, SpotBugs, PMD, JaCoCo, or Spotless.
6. Prefer **fail the build** on new code; use temporary `ignoreFailures` or baselines only with a tracked cleanup ticket.
7. Align local commands with CI so developers reproduce failures before push.
8. Cross-check test design with `java-testing-verification`; gate strategy with `testing-strategy`.

## Gate Checklist

- PR pipeline runs unit tests (and integration tests when risk warrants).
- Static analysis runs on main sources — not skipped silently in CI.
- Formatting is enforced (Spotless, formatter plugin, or `./mvn fmt`) — not optional manual cleanup.
- Coverage thresholds apply to **meaningful packages**, not blanket noise on DTOs.
- JDK/toolchain in CI matches `build` files (`jenv`, `.java-version`, or toolchain block).
- `./gradlew check` or `./mvn verify` passes locally before claiming done.

## Output

Summarize:

- **Gates in scope** — which tools and thresholds
- **CI jobs** — commands per stage (PR vs main)
- **Changes made** — build files, config paths, workflow YAML
- **Commands to verify** — exact local and CI equivalents
- **Deferred items** — baselines, `ignoreFailures`, coverage exclusions with ticket refs

## Related Skills

- `java-testing-verification` — test design and Testcontainers
- `testing-strategy` — pyramid and cross-stack gate policy
- `java-core-engineering` — code changes behind gate failures
