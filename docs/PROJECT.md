# Project Context

> Complete each placeholder with project-specific facts. Mark unknown information as `Unknown` and link evidence when it becomes available.

## Mission

myIR is a Java Information Retrieval learning and experimentation platform for understanding and testing classical retrieval techniques from first principles.

## Scope

### In scope

In-memory lexical and sparse-vector retrieval; reusable web acquisition and extraction; applications that exercise those capabilities.

### Out of scope

Production-scale persistence, dense semantic retrieval, and required integration between independent applications such as site export and search.

## Current State

IR-0 through IR-4 are complete. IR-5 Score Explanation and IR-6 Evaluation Harness are unselected roadmap candidates. Current storage is intentionally in memory; web resource lifecycle, HTTP integration coverage, dynamic fetching, and concurrent weighting are maintenance questions.

## Architecture

[Current System and Architectural Boundaries](knowledge/architecture/current-system.md)

## Technology

Java 25, Maven multi-module JPMS build, JUnit 5, in-memory corpus/index/vector/vocabulary stores. Validate with `mvn compile` and `mvn test`.

## Repository Map

`codex-ir-core/` is the IR engine; `codex-ir-web/` is reusable web acquisition/extraction; `codex-ir-app/` contains applications. Direction is `app → web → core`. Documentation is organized under `docs/knowledge/`, `docs/engineering/`, `docs/adr/`, and `docs/roadmap/`.

## Getting Started

Install Java 25 and Maven, then run `mvn test`. Install Playwright browsers with `npx playwright install` only before tests requiring a browser runtime.

## Important References

- [Workspace operating guide](OSK.md)
- [Project knowledge](knowledge/README.md)
- [Current architecture](knowledge/architecture/current-system.md)
- [Roadmap](roadmap/ROADMAP.md)
- [State of the Art assessment](engineering/agents/reports/myir-state-of-the-art-2026-09.md)
