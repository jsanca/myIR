---
type: Engineering Log Entry
title: "Phase 5 — Robust Manifest Metadata + Manifest Hygiene"
description: "Canonical engineering evidence for Phase 5 — Robust Manifest Metadata + Manifest Hygiene."
tags: [site-exporter, engineering-log, migration]
timestamp: 2026-07-05T00:00:00Z
ckf_version: "0.1"
ckf_status: completed
ckf_scope: site-exporter
ckf_owner: project
---
# Phase 5 — Robust Manifest Metadata + Manifest Hygiene

### Summary

Replaced the Phase 4 prototype manifest with a stable, portable contract. Hand-crafted JSON was replaced with Jackson. The `MirroredPage` record was expanded from 5 fields to 13. The `MirrorManifest` record was expanded from 3 fields to 11 and gains a Builder. Failed page writes now produce a `WRITE_FAILED` entry in the manifest rather than being silently dropped. `LocalPathResolver` now strips `..`/`.` segments and filesystem-unsafe characters, and verifies every resolved path stays within the output directory. `ManifestWriter` and `ManifestReader` are new public types.

### Scope

**Included:**
- Jackson-based serialization/deserialization replacing hand-crafted JSON
- Expanded `MirroredPage` schema (id, canonicalUrl, localHtmlPath as relative string, depth, discoveredOrder, parentUrl, contentType, status, fetchedAt, mirrorStatus, errorMessage)
- Expanded `MirrorManifest` schema (manifestVersion, startUrl, generatedAt, sameDomainOnly, maxPages, maxDepth, documentCount, successfulCount, failedCount, skippedCount)
- `MirrorStatus` enum: SUCCESS, FETCH_FAILED, WRITE_FAILED, SKIPPED
- `ManifestWriter` — serializes manifest to JSON file
- `ManifestReader` — deserializes manifest from JSON file
- `MirrorManifest.readFrom()` static factory for round-trip convenience
- Sanitized `LocalPathResolver`: strips `..`/`.`, replaces `[\\x00\\:*?"<>|]` with `_`, enforces stay-within-outputDir invariant
- `WRITE_FAILED` tracking in `SiteMirrorService`

**Explicitly excluded:**
- Asset manifest and download
- Link rewriting
- PDF/ePub rendering
- FETCH_FAILED and SKIPPED tracking (crawler API does not expose these events)
- `WebCrawlerRuntime` lifecycle ownership improvement

### Deliverables

- `MirrorStatus.java` — new enum
- `MirroredPage.java` — expanded record + Builder
- `MirrorManifest.java` — expanded record + Builder; delegates to ManifestWriter/Reader
- `ManifestWriter.java` — new class; Jackson-based serializer
- `ManifestReader.java` — new class; Jackson-based deserializer
- `LocalPathResolver.java` — sanitization + traversal guard
- `SiteMirrorService.java` — WRITE_FAILED tracking, relative paths, new manifest builder
- `ManifestWriterTest.java` — 6 new tests (write, round-trip, nullable fields, WRITE_FAILED, empty list, multi-page)
- `LocalPathResolverTest.java` — 7 new tests added (dot-dot stripping, dot stripping, only-dot-dot fallback, colon replace, backslash replace, traversal guard ×2)
- `SiteMirrorServiceTest.java` — updated 6 existing tests + added 3 new tests (WRITE_FAILED, relative paths, manifest config metadata)
- `codex-ir-app/pom.xml` — added `jackson-databind` dependency
- `module-info.java` — added `requires com.fasterxml.jackson.databind`

### Changed Files

| File | Change |
|---|---|
| `codex-ir-app/pom.xml` | Added `jackson-databind` dependency |
| `codex-ir-app/src/main/java/module-info.java` | Added `requires com.fasterxml.jackson.databind` |
| `codex-ir-app/src/main/java/codex/apps/siteexporter/MirrorStatus.java` | New |
| `codex-ir-app/src/main/java/codex/apps/siteexporter/MirroredPage.java` | Rewritten |
| `codex-ir-app/src/main/java/codex/apps/siteexporter/MirrorManifest.java` | Rewritten |
| `codex-ir-app/src/main/java/codex/apps/siteexporter/ManifestWriter.java` | New |
| `codex-ir-app/src/main/java/codex/apps/siteexporter/ManifestReader.java` | New |
| `codex-ir-app/src/main/java/codex/apps/siteexporter/LocalPathResolver.java` | Rewritten |
| `codex-ir-app/src/main/java/codex/apps/siteexporter/SiteMirrorService.java` | Rewritten |
| `codex-ir-app/src/test/java/codex/apps/siteexporter/ManifestWriterTest.java` | New |
| `codex-ir-app/src/test/java/codex/apps/siteexporter/LocalPathResolverTest.java` | Updated |
| `codex-ir-app/src/test/java/codex/apps/siteexporter/SiteMirrorServiceTest.java` | Updated |

### Validation

```
mvn test -pl codex-ir-app
Tests run: 48, Failures: 0, Errors: 0, Skipped: 0
```

- `SitemapUrlExtractorTest`: 8/8 (pre-existing, no regression)
- `LocalPathResolverTest`: 15/15 (8 original + 7 new sanitization/traversal tests)
- `SiteExporterCommandTest`: 6/6 (no changes needed)
- `HtmlPageWriterTest`: 4/4 (no changes needed)
- `SiteMirrorServiceTest`: 9/9 (6 updated + 3 new)
- `ManifestWriterTest`: 6/6 (new)

Manual inspection of manifest JSON output confirmed:
- `startUrl`, `generatedAt`, `documentCount`, `successfulCount`, `failedCount`, `skippedCount` present at manifest level
- Per-page `localHtmlPath` is relative (`section/page/index.html`, not an absolute path)
- `mirrorStatus` field present with value `SUCCESS` or `WRITE_FAILED`
- Failed page entries include `errorMessage` and `null` for `localHtmlPath`

### Tests

| Test class | Tests | Coverage |
|---|---|---|
| `ManifestWriterTest` | 6 new | write, round-trip SUCCESS, round-trip WRITE_FAILED, nullable fields, empty list, multi-page with mixed statuses |
| `LocalPathResolverTest` | +7 | dot-dot stripping, dot stripping, all-dot-dot fallback to index, colon replacement, backslash replacement (via %5C), traversal guard ×2 |
| `SiteMirrorServiceTest` | +3 | WRITE_FAILED tracking, relative paths, manifest config metadata (maxPages, maxDepth, sameDomainOnly, manifestVersion) |

### Engineering Notes

- `ManifestReader` recomputes `documentCount`, `successfulCount`, `failedCount`, `skippedCount` from the pages list using the Builder, ignoring the stored count fields in JSON. This ensures consistency even if the JSON was manually edited.
- `LocalPathResolver` uses `Path.toAbsolutePath().normalize()` for the traversal guard comparison, ensuring symlinks and `.` components in `outputDir` don't cause false positives.
- `SiteMirrorService.mirror()` uses `AtomicInteger` for `discoveredOrder` inside the lambda (required because lambdas can only capture effectively-final references, not mutable primitives).
- Jackson `ObjectMapper` instances in `ManifestWriter` and `ManifestReader` are static final to avoid repeated construction overhead.
- `localHtmlPath` is stored as a `String` in the record (not `Path`) to avoid platform-specific `Path.toString()` behavior. Separators are normalized to `/` at assignment time.

### Decisions

- **Jackson ObjectNode/JsonNode approach over POJO binding** — avoids the need for `@JsonProperty` on every record constructor parameter and doesn't require `jackson-module-parameter-names`. The tree-building approach is explicit and has no magic.
- **Counts computed from pages in Builder** — rather than accepting them as builder parameters, counts are always derived from the pages list. This guarantees consistency even after manual edits to the JSON.
- **MirroredPage.localHtmlPath as `String`** — storing as `String` rather than `Path` avoids `Path.toString()` returning OS-specific separators on Windows.

### Tradeoffs

- **FETCH_FAILED and SKIPPED not yet tracked** — the current `DocumentSource.readInto()` contract only delivers successfully-fetched pages. Tracking fetch failures requires a crawler API change. These statuses are defined in the enum but unused; the manifest will always show 0 for `failedCount` for fetch failures. Documented as a known limitation.
- **depth and parentUrl always 0/null** — the crawler does not expose per-page crawl depth or parent URL through the `WebPage` type. Fields are wired but set to sentinel defaults.

### Risks

- **Jackson module on module path** — `codex-ir-app` now directly requires `com.fasterxml.jackson.databind`. This is correct but means the Jackson version is fixed at the parent pom's `2.17.2`. Any upgrade must be coordinated across modules.
- **No Jackson module for Java time** — `Instant` is serialized via `Instant.toString()` and parsed back with `Instant.parse()`. This relies on ISO-8601 format consistency, which is stable, but it's manual rather than using `jackson-datatype-jsr310`. The risk is negligible for this tool.

### Known Limitations

- `FETCH_FAILED` and `SKIPPED` mirror statuses are defined but never emitted. Crawler API changes are required.
- `depth` and `parentUrl` per-page fields are always `0`/`null`. Requires crawler to surface traversal metadata.
- No live-site integration test. All tests use `@TempDir` + injected `DocumentSource`.

### Follow-ups

- Expose crawl depth and parent URL from the traversal crawler to populate those `MirroredPage` fields.
- Investigate FETCH_FAILED tracking via a failure callback in `DocumentSource` or a separate event channel.
- Consider adding `jackson-datatype-jsr310` for more robust Java time handling.

### Next Step

**Phase 6 — Asset Download and Link Rewriting**: download CSS, images, and JS referenced by mirrored HTML pages; rewrite internal links so the mirror is locally navigable. This is the prerequisite for PDF rendering from the mirrored output.

---
