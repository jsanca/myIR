---
type: Engineering Log Entry
title: "Phase 10 — PDF Assembly"
description: "Canonical engineering evidence for Phase 10 — PDF Assembly."
tags: [site-exporter, engineering-log, migration]
timestamp: 2026-07-05T00:00:00Z
ckf_version: "0.1"
ckf_status: completed
ckf_scope: site-exporter
ckf_owner: project
---
# Phase 10 — PDF Assembly

### Summary

Completed the PDF assembly layer by introducing `ManifestOrderPdfAssemblyStrategy` (the production `PdfAssemblyStrategy`), `AssemblyReport` (structured per-run metrics), per-page render failure recovery in `PublicationPipeline.run()`, and `AssemblyReport` as a field of `PublicationArtifact`. The pipeline no longer aborts on a single page render failure — it skips the failed page, records the failure, and assembles from what succeeded.

### Scope

**Included:**
- `ManifestOrderPdfAssemblyStrategy` — production assembler, delegates to `PdfBoxMergeStrategy`
- `AssemblyReport` record — `pagesAttempted`, `pagesRendered`, `pagesFailed`, `outputSizeBytes`, `List<RenderFailure>`
- `AssemblyReport.RenderFailure` nested record — `pageUrl`, `reason`
- `PublicationArtifact` — added `assemblyReport` field
- `PublicationPipeline.run()` — per-page failure catching; report construction
- `SiteExporterCommand` — uses `ManifestOrderPdfAssemblyStrategy`; logs assembly report
- Tests for `ManifestOrderPdfAssemblyStrategy` and the new pipeline assembly report behaviour

**Excluded:**
- TOC generation
- ePub assembly
- Assembly result persistence (only in-memory + logged)
- PDF page numbering or merge metadata

### Deliverables

- `AssemblyReport.java` — new record with nested `RenderFailure`
- `ManifestOrderPdfAssemblyStrategy.java` — new concrete `PdfAssemblyStrategy`
- `ManifestOrderPdfAssemblyStrategyTest.java` — 5 tests
- Updated `PublicationArtifact.java` — `assemblyReport` field added
- Updated `PublicationPipeline.java` — per-page failure recovery, report construction
- Updated `SiteExporterCommand.java` — uses `ManifestOrderPdfAssemblyStrategy`, logs report
- Updated `PublicationPipelineTest.java` — 4 new assembly report tests

### Changed Files

| File | Change |
|---|---|
| `codex-ir-app/src/main/java/codex/apps/siteexporter/AssemblyReport.java` | Created |
| `codex-ir-app/src/main/java/codex/apps/siteexporter/ManifestOrderPdfAssemblyStrategy.java` | Created |
| `codex-ir-app/src/test/java/codex/apps/siteexporter/ManifestOrderPdfAssemblyStrategyTest.java` | Created |
| `codex-ir-app/src/main/java/codex/apps/siteexporter/PublicationArtifact.java` | Updated |
| `codex-ir-app/src/main/java/codex/apps/siteexporter/PublicationPipeline.java` | Updated |
| `codex-ir-app/src/main/java/codex/apps/siteexporter/SiteExporterCommand.java` | Updated |
| `codex-ir-app/src/test/java/codex/apps/siteexporter/PublicationPipelineTest.java` | Updated |

### Validation

```
mvn test -pl codex-ir-web,codex-ir-app
BUILD SUCCESS
```

siteexporter results:
- `ManifestOrderPdfAssemblyStrategyTest` — 5 tests
- `PublicationPipelineTest` — 25 tests (was 21; added 4 assembly report tests)
- All other siteexporter tests unchanged: 0 failures

### Tests

| Test class | Tests | What is covered |
|---|---|---|
| `ManifestOrderPdfAssemblyStrategyTest` | 5 | Empty → `byte[0]`; null guard; single page; two pages; merged size |
| `PublicationPipelineTest` (new) | 4 | Report on success; render failure recorded + pipeline continues; outputSizeBytes in report; all-fail produces zero-byte artifact |
| `PublicationPipelineTest` (updated) | — | Added `assertNotNull(artifact.assemblyReport())` to metadata test; `pagesAttempted == 0` for empty manifest |

### Engineering Notes

- `PublicationPipeline.run()` catches `IOException` per page. Any other `RuntimeException` from the renderer still propagates and aborts the pipeline — callers are expected to throw only `IOException` for rendering errors.
- `AssemblyReport.renderFailures` is defensively copied in the compact constructor (`List.copyOf`), consistent with other records in this codebase.
- `ManifestOrderPdfAssemblyStrategy` is intentionally a thin wrapper — the PDF merge logic lives in `PdfBoxMergeStrategy` which can be used standalone (e.g. in tests) without the manifest-ordering semantics.
- `PublicationArtifact` compact constructor now requires `assemblyReport` non-null. All existing callers go through `PublicationPipeline.run()`, so no external breakage.

### Decisions

- Made `ManifestOrderPdfAssemblyStrategy` delegate to `PdfBoxMergeStrategy` rather than duplicate the merge logic. `PdfBoxMergeStrategy` stays as the low-level primitive; `ManifestOrderPdfAssemblyStrategy` is the semantically named production strategy.
- Per-page failure is caught and recorded rather than aborting. The rationale: a mirror of 200 pages where one page fails to render should still produce a 199-page PDF, not nothing.
- `AssemblyReport.RenderFailure` is a nested record of `AssemblyReport` because it has no meaning outside the report context.

### Tradeoffs

| Choice | Alternative | Reason |
|---|---|---|
| Catch `IOException` per page | Let any failure abort | Partial output is better than no output for large mirrors |
| `RuntimeException` still propagates | Catch all exceptions | Distinguishes expected I/O errors from programming errors |
| `AssemblyReport` in `PublicationArtifact` | Return a pair `(artifact, report)` | Simpler API; the artifact already represents the full run result |
| `ManifestOrderPdfAssemblyStrategy` delegates to `PdfBoxMergeStrategy` | Inline PDFBox logic | Keeps merge logic in one place |

### Risks

- A renderer that throws `RuntimeException` instead of `IOException` will abort the pipeline silently (from the report's perspective). Callers must throw `IOException` for render errors.
- `AssemblyReport` is not persisted — if the CLI exits normally, the report is printed to stdout/stderr only. A follow-up could write it to a JSON file alongside the artifact.

### Known Limitations

- No per-page PDF size in the report (only total output size).
- `ManifestOrderPdfAssemblyStrategy` does not enforce ordering itself — it trusts that the pages arrive in the order determined by `PublicationOrderingStrategy`.
- Assembly report is not written to disk.

### Follow-ups

- Write `assembly-report.json` to the output directory alongside the PDF.
- Add per-page render timing to `AssemblyReport`.
- Phase 11: review `PublicationFormat` abstraction for ePub readiness; document extension points.

### Next Step

Phase 11 — Future Format Readiness: review `PublicationFormat` abstraction, confirm no PDF-only concepts leak into the generic pipeline, and document the ePub extension point.

---
