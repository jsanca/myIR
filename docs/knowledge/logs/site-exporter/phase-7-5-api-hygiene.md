---
type: Engineering Log Entry
title: "Phase 7.5 — API Hygiene"
description: "Canonical engineering evidence for Phase 7.5 — API Hygiene."
tags: [site-exporter, engineering-log, migration]
timestamp: 2026-07-05T00:00:00Z
ckf_version: "0.1"
ckf_status: completed
ckf_scope: site-exporter
ckf_owner: project
---
# Phase 7.5 — API Hygiene

### Summary

Three targeted hygiene fixes based on Deep's package review. `ManifestWriter` and `ManifestReader` made package-private (they are implementation details accessible only through `MirrorManifest.writeTo()` and `MirrorManifest.readFrom()`). `PublicationPipeline` gained a `.format(PublicationFormat)` builder setter (defaulting to `PDF`) and a `format()` accessor so the artifact format is driven by the caller, not hardcoded. Two new pipeline builder tests cover the default and the override. Engineering note added for the flat-package decision.

### Scope

**Included:**
- `ManifestWriter`: `public final class` → `final class`
- `ManifestReader`: `public final class` → `final class`
- `PublicationPipeline.Builder`: added `format` field (default `PublicationFormat.PDF`) and `.format()` setter
- `PublicationPipeline`: added `format` field and `format()` accessor; `run()` uses `this.format` instead of hardcoded `PublicationFormat.PDF`
- 2 new tests: `formatDefaultsToPdf`, `formatCanBeOverriddenToEpub`; existing `accessorsShouldReturn…` updated to assert `format()`
- Engineering note on flat-package decision in `ENGINEERING_LOG.md`

**Not changed:**
- No subpackages created
- `AssetFetcher` remains package-private (was already)
- `AssetManifestWriter`, `AssetManifestReader` remain package-private (were already)
- No CLI wiring, no PDF rendering

### Changed Files

| File | Change |
|---|---|
| `codex-ir-app/src/main/java/codex/apps/siteexporter/ManifestWriter.java` | `public` removed |
| `codex-ir-app/src/main/java/codex/apps/siteexporter/ManifestReader.java` | `public` removed |
| `codex-ir-app/src/main/java/codex/apps/siteexporter/PublicationPipeline.java` | Added `format` field + accessor; Builder `.format()` setter with default PDF |
| `codex-ir-app/src/test/java/codex/apps/siteexporter/PublicationPipelineTest.java` | +2 format tests; updated `accessorsShouldReturn…` |

### Validation

```
mvn test
Tests run: 127, Failures: 0, Errors: 0, Skipped: 0
```

### Engineering Notes

**Flat package structure — intentional, deferred until necessary.**

All site-exporter types live in a single flat package (`codex.apps.siteexporter`). This is a deliberate choice for this phase:

- `package-private` visibility is the primary encapsulation mechanism. Types such as `HtmlLinkRewriter`, `PageLinkRewritePlan`, `AssetLinkRewritePlan`, `AssetFetcher`, `AssetReference`, `AssetReferenceExtractor`, `AssetLocalPathResolver`, `AssetManifestWriter`, `AssetManifestReader`, `ManifestWriter`, and `ManifestReader` are hidden from all callers outside the package without any `exports` or `opens` ceremony.
- Moving to subpackages (e.g. `siteexporter.manifest`, `siteexporter.assets`, `siteexporter.rewrite`) would require adding `opens` directives to `module-info.java` for each subpackage that Jackson needs to access via reflection, and would turn every package-private type into either `public` or accessible only through deliberate cross-package references.
- The package will be split when it exceeds approximately 50 types or when the package-private boundary becomes painful to reason about — whichever comes first. At the current ~30 types, the flat structure is still coherent.

### Next Step

**Phase 8 — PDF Renderer Port**: implement `PdfRenderer` using OpenHTMLToPDF.

---
