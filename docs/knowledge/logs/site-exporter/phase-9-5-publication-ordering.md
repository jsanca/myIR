---
type: Engineering Log Entry
title: "Phase 9.5 — Publication Ordering Strategy"
description: "Canonical engineering evidence for Phase 9.5 — Publication Ordering Strategy."
tags: [site-exporter, engineering-log, migration]
timestamp: 2026-07-05T00:00:00Z
ckf_version: "0.1"
ckf_status: completed
ckf_scope: site-exporter
ckf_owner: project
---
# Phase 9.5 — Publication Ordering Strategy

### Summary

Extracted page ordering out of `PublicationPipeline.run()` into a dedicated `PublicationOrderingStrategy` interface. The default implementation (`DiscoveredOrderPublicationOrderingStrategy`) filters to SUCCESS pages with a non-null `localHtmlPath` and sorts by `discoveredOrder` ascending — preserving the previous behaviour while making ordering an explicit, swappable concern.

### Scope

**Included:**
- `PublicationOrderingStrategy` interface
- `DiscoveredOrderPublicationOrderingStrategy` default implementation
- `PublicationPipeline.Builder.orderingStrategy(...)` optional setter (defaults to `DiscoveredOrderPublicationOrderingStrategy`)
- `PublicationPipeline.run()` delegates to `orderingStrategy.order(source.manifest())`
- Tests for the new strategy and pipeline integration

**Excluded:**
- TOC-based ordering
- Heuristic ordering
- PDF assembly changes
- CLI changes
- ePub

### Deliverables

- `PublicationOrderingStrategy.java` — new interface
- `DiscoveredOrderPublicationOrderingStrategy.java` — new default implementation
- `DiscoveredOrderPublicationOrderingStrategyTest.java` — 6 tests
- Updated `PublicationPipeline.java` — field, accessor, builder method, and `run()` delegation
- Updated `PublicationPipelineTest.java` — 5 new tests for ordering strategy

### Changed Files

| File | Change |
|---|---|
| `codex-ir-app/src/main/java/codex/apps/siteexporter/PublicationOrderingStrategy.java` | Created |
| `codex-ir-app/src/main/java/codex/apps/siteexporter/DiscoveredOrderPublicationOrderingStrategy.java` | Created |
| `codex-ir-app/src/test/java/codex/apps/siteexporter/DiscoveredOrderPublicationOrderingStrategyTest.java` | Created |
| `codex-ir-app/src/main/java/codex/apps/siteexporter/PublicationPipeline.java` | Updated |
| `codex-ir-app/src/test/java/codex/apps/siteexporter/PublicationPipelineTest.java` | Updated |

### Validation

```
mvn test -pl codex-ir-web,codex-ir-app
BUILD SUCCESS  (total tests: 150+ across both modules, 0 failures)
```

siteexporter-specific results:
- `DiscoveredOrderPublicationOrderingStrategyTest` — 6 tests
- `PublicationPipelineTest` — 21 tests (was 17; added 5 ordering tests)

### Tests

| Test class | Tests | What is covered |
|---|---|---|
| `DiscoveredOrderPublicationOrderingStrategyTest` | 6 | Ascending sort; WRITE_FAILED excluded; FETCH_FAILED excluded; null localHtmlPath excluded; empty manifest; all-excluded manifest |
| `PublicationPipelineTest` (new tests) | 5 | Default strategy is `DiscoveredOrderPublicationOrderingStrategy`; custom strategy override; null strategy throws; `run()` respects strategy order; assembler receives only strategy-selected pages |

### Engineering Notes

- `PublicationPipeline.run()` previously contained the filter+sort inline. Delegating to the strategy makes the pipeline responsible only for iteration and rendering, not for deciding which pages to render.
- The ordering strategy is optional in the builder — the default is wired in the `Builder` field initialiser, so existing callers need no changes.
- `DiscoveredOrderPublicationOrderingStrategy` returns an unmodifiable list (`Stream.toList()` in Java 16+), consistent with the rest of the codebase.

### Decisions

- Made `orderingStrategy` optional (defaulted) rather than required. Requiring it would break every existing `PublicationPipeline.builder()...build()` call without adding value for the common case.
- Kept the interface in the same package as `PublicationPipeline` rather than introducing a sub-package. The interface is narrow and there is only one implementation so far.

### Tradeoffs

| Choice | Alternative | Reason |
|---|---|---|
| Single interface method `order(MirrorManifest)` | `order(List<MirroredPage>)` | Gives the strategy access to manifest-level metadata (startUrl, maxDepth) which may be useful for future ordering heuristics |
| Default wired in Builder field | Factory method `PublicationOrderingStrategies.discovered()` | Simpler; the Interface+Factory pattern is reserved for richer families; one implementation doesn't warrant it yet |

### Risks

- A custom ordering strategy that returns pages not present in the manifest could cause `contentDir.resolve(page.localHtmlPath())` to fail at render time. No defensive check is added — callers are trusted to return valid pages.

### Known Limitations

- No validation that the ordered pages are actually members of the manifest.
- Discovery order is not a reading order — for documentation sites with deep hierarchies, a URL-path-based ordering may be more useful.

### Follow-ups

- Phase 10: `ManifestOrderPdfAssemblyStrategy` (explicit ordering by comparator or TOC).
- Consider `TitleAlphabeticPublicationOrderingStrategy` for alphabetically sorted output.
- Add a `reverseOrder()` decorator that wraps any strategy.

### Next Step

Phase 10 — PDF Assembly: implement `ManifestOrderPdfAssemblyStrategy`, add an assembly report, and handle missing or failed render entries gracefully.

---
