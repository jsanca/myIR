---
type: Engineering Log Entry
title: "Phase 10.8 — Resume From Existing Mirror (`--from-mirror`)"
description: "Canonical engineering evidence for Phase 10.8 — Resume From Existing Mirror (`--from-mirror`)."
tags: [site-exporter, engineering-log, migration]
timestamp: 2026-07-05T00:00:00Z
ckf_version: "0.1"
ckf_status: completed
ckf_scope: site-exporter
ckf_owner: project
---
# Phase 10.8 — Resume From Existing Mirror (`--from-mirror`)

### Summary

Extended `SiteExporterCommand` with a `--from-mirror <path>` option that skips the crawl phase entirely and reuses a previously created mirror. A new `ExistingMirrorLoader` class reads `mirror-manifest.json` from the provided directory, validates it (hard-fail on missing dir or manifest, soft-warn on missing HTML files), and returns the manifest for use by the downstream pipeline stages unchanged. When `--from-mirror` is present, `SiteMirrorService` is never called.

### Scope

**Included:**
- `ExistingMirrorLoader` — new class with hard/soft validation of an existing mirror
- `SiteExporterCommand` — updated `ParsedArgs` record (new `fromMirrorDir` field), updated `parseArgs()`, updated `main()` with resume branch
- 13 new tests in `ExistingMirrorLoaderTest` and 7 new tests in `SiteExporterCommandTest`

**Excluded:**
- Changes to `MirrorManifest` schema
- Changes to `SiteMirrorService` or any crawler code
- Re-downloading any assets that already exist in the mirror
- Partial-mirror resumption (e.g. resuming a crawl that was interrupted mid-run)

### Deliverables

- `ExistingMirrorLoader.java` — new class (79 source lines)
- `ExistingMirrorLoaderTest.java` — 13 tests
- `SiteExporterCommand.java` — updated CLI parser and `main()` logic
- `SiteExporterCommandTest.java` — 7 new tests for `--from-mirror` flag

### Changed Files

| File | Change |
|---|---|
| `codex-ir-app/src/main/java/codex/apps/siteexporter/ExistingMirrorLoader.java` | Created |
| `codex-ir-app/src/main/java/codex/apps/siteexporter/SiteExporterCommand.java` | Updated (resume branch, `fromMirrorDir` field) |
| `codex-ir-app/src/test/java/codex/apps/siteexporter/ExistingMirrorLoaderTest.java` | Created |
| `codex-ir-app/src/test/java/codex/apps/siteexporter/SiteExporterCommandTest.java` | Updated (7 new tests) |

### Validation

```
mvn test -pl codex-ir-app
Tests run: 261, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

- `ExistingMirrorLoaderTest.loadedManifestShouldSupportPublicationPipelineRun` runs the full pipeline (load manifest → render two HTML pages → assemble PDF) and confirms `%PDF` output ✓
- All prior tests (243 before this phase) continue to pass ✓
- Resume branch in `main()` not exercised by unit tests (requires a live mirror directory); covered by `ExistingMirrorLoaderTest` integration test ✓

### Tests

| Test | Class | Status |
|---|---|---|
| `loadShouldThrowWhenMirrorDirIsNull` | `ExistingMirrorLoaderTest` | Added |
| `loadShouldThrowWhenMirrorDirDoesNotExist` | `ExistingMirrorLoaderTest` | Added |
| `loadShouldThrowWhenManifestFileIsMissing` | `ExistingMirrorLoaderTest` | Added |
| `loadShouldThrowWhenMirrorPathIsAFile` | `ExistingMirrorLoaderTest` | Added |
| `loadShouldReturnManifestForValidMirrorDirectory` | `ExistingMirrorLoaderTest` | Added |
| `loadShouldPreserveAllManifestFields` | `ExistingMirrorLoaderTest` | Added |
| `loadShouldSucceedWhenAllSuccessPageHtmlFilesExist` | `ExistingMirrorLoaderTest` | Added |
| `loadShouldSucceedEvenWhenSomeHtmlFilesAreMissing` | `ExistingMirrorLoaderTest` | Added |
| `loadShouldIgnoreNonSuccessPagesDuringFileValidation` | `ExistingMirrorLoaderTest` | Added |
| `loadShouldIgnoreSuccessPagesWithNullLocalHtmlPath` | `ExistingMirrorLoaderTest` | Added |
| `loadedManifestShouldSupportPublicationPipelineRun` | `ExistingMirrorLoaderTest` | Added |
| `parseArgsShouldSetFromMirrorDir` | `SiteExporterCommandTest` | Added |
| `parseArgsShouldNotRequireUrlWhenFromMirrorIsProvided` | `SiteExporterCommandTest` | Added |
| `parseArgsShouldUseFromMirrorDirAsDefaultOutputDir` | `SiteExporterCommandTest` | Added |
| `parseArgsShouldRespectExplicitOutDirEvenWithFromMirror` | `SiteExporterCommandTest` | Added |
| `shouldThrowWhenNeitherUrlNorFromMirrorIsProvided` | `SiteExporterCommandTest` | Added (replaces prior `shouldThrowWhenUrlFlagIsMissing` semantics) |
| `parseArgsShouldAcceptBothUrlAndFromMirrorTogether` | `SiteExporterCommandTest` | Added |
| `parseArgsShouldLeaveFromMirrorDirNullInNormalMode` | `SiteExporterCommandTest` | Added |

### Engineering Notes

- `ExistingMirrorLoader.validateHtmlFiles()` is intentionally tolerant: missing HTML files produce a `[ExistingMirrorLoader][WARN]` line per file on `stderr` plus a count summary, but do not abort loading. Render failures for those pages surface later in `AssemblyReport.renderFailures()` where they belong.
- In resume mode, `SiteMirrorOptions` is reconstructed from manifest metadata (seedUrl, maxPages, maxDepth, sameDomainOnly) so that `SiteAssetService` and `SiteLinkRewriteService` receive valid options without requiring a second `--url` flag.
- The `outputDir` defaults to `fromMirrorDir` when `--out-dir` is not explicitly provided, so asset downloads and rewrites go into the same directory tree as the existing mirror (which is the expected layout).
- `--url` is optional when `--from-mirror` is set. If both are provided, `--url` is silently ignored for the mirror phase (but the value is still parsed and stored in `ParsedArgs.seedUrl` for forward compatibility).

### Decisions

- **`ExistingMirrorLoader` as a standalone class** (not a static helper on `SiteExporterCommand`): the loader has its own validation contract and is tested independently; embedding it inside the command would make that contract untestable in isolation.
- **`IOException` for hard failures, `stderr` for soft failures**: consistent with how `SiteMirrorService` reports errors. Hard failures cause `main()` to print the message and `System.exit(1)`. Soft warnings do not interrupt the pipeline.
- **`fromMirrorDir` nullable in `ParsedArgs`**: null is the clear sentinel for "not provided". An `Optional<Path>` would add noise to the record's constructor and downstream null checks without benefit.

### Tradeoffs

- **No partial-resume**: if a crawl was interrupted after saving some pages but not all, this path re-uses whatever is on disk. Pages with missing HTML files produce render failures in `AssemblyReport`. A future partial-resume feature would need `SiteMirrorService` changes and is out of scope.
- **No manifest schema version check**: `ManifestReader` reads any valid `mirror-manifest.json`. If the schema changes in the future, manifests written by older versions may silently read back with zero-value fields. An explicit schema version field could guard against this.

### Risks

- If the user provides a `--from-mirror` directory that was written by a future version of the tool with additional required fields, `ManifestReader` may silently read back nulls or defaults. Low risk now; worth tracking if the schema evolves.
- `SiteAssetService.download()` and `SiteLinkRewriteService.rewrite()` are both called in resume mode. If assets were already downloaded and links already rewritten in the original run, these steps are idempotent for downloads (files already exist) but link rewriting may transform already-rewritten links. This is pre-existing behavior shared with the normal path.

### Known Limitations

- Resume mode does not skip asset download or link rewriting, even if those steps completed in the original run. The pipeline always runs all three post-mirror stages.
- `loadedManifestShouldSupportPublicationPipelineRun` validates the load-and-render path but does not exercise `SiteAssetService` or `SiteLinkRewriteService` (those require network access or a full mirror fixture).

### Follow-ups

1. Add a `--skip-assets` / `--skip-links` flag to allow skipping already-completed pipeline stages in resume mode.
2. Add a schema version field to `MirrorManifest` so future incompatible changes can be detected at load time.
3. Support partial-mirror resumption: detect which pages are still missing and continue crawling from those URLs.

### Next Step

Validate resume mode end-to-end using a real previously-downloaded mirror directory: run the full pipeline once (`--url`), then re-run with `--from-mirror` and confirm identical output artifacts without triggering any network requests.
