---
type: Engineering Log Entry
title: "Phase 9 — CLI Wiring and PDF Assembly"
description: "Canonical engineering evidence for Phase 9 — CLI Wiring and PDF Assembly."
tags: [site-exporter, engineering-log, migration]
timestamp: 2026-07-05T00:00:00Z
ckf_version: "0.1"
ckf_status: completed
ckf_scope: site-exporter
ckf_owner: project
---
# Phase 9 — CLI Wiring and PDF Assembly

### Summary

Completed the end-to-end publication pipeline by implementing `PdfBoxMergeStrategy` (the concrete `PdfAssemblyStrategy` using Apache PDFBox `PDFMergerUtility`) and wiring `SiteExporterCommand.main()` to run the full flow: mirror → asset download → link rewrite → render → assemble → write artifact. The CLI stub is replaced with real pipeline invocation.

### Scope

**Included:**
- `PdfBoxMergeStrategy` — merges per-page PDF byte arrays into one combined PDF
- `SiteExporterCommand` — replaces stub with `SiteAssetService` → `SiteLinkRewriteService` → `PublicationPipeline` chain
- `module-info.java` — added `requires org.apache.pdfbox;`
- Tests for `PdfBoxMergeStrategy`

**Excluded:**
- `PdfExportService` (per-page export service) — subsumed by `PublicationPipeline.run()` which already handles per-page rendering
- ePub support
- Asset manifest validation in CLI
- Progress reporting / structured logging

### Deliverables

- `PdfBoxMergeStrategy.java` — `PdfAssemblyStrategy` backed by `PDFMergerUtility`
- `PdfBoxMergeStrategyTest.java` — 5 tests
- Updated `SiteExporterCommand.java` — full pipeline wired
- Updated `module-info.java` — `requires org.apache.pdfbox;`

### Changed Files

| File | Change |
|---|---|
| `codex-ir-app/src/main/java/codex/apps/siteexporter/PdfBoxMergeStrategy.java` | Created |
| `codex-ir-app/src/test/java/codex/apps/siteexporter/PdfBoxMergeStrategyTest.java` | Created |
| `codex-ir-app/src/main/java/codex/apps/siteexporter/SiteExporterCommand.java` | Updated |
| `codex-ir-app/src/main/java/module-info.java` | Updated |

### Validation

```
mvn test -pl codex-ir-web,codex-ir-app
Tests run: 137, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

All 137 tests passed. `PdfBoxMergeStrategyTest` (5 tests) and `OpenHtmlToPdfRendererTest` (5 tests) ran with real PDF rendering against OpenHTMLToPDF.

### Tests

| Test class | Tests | Description |
|---|---|---|
| `PdfBoxMergeStrategyTest` | 5 | Empty list → `byte[0]`; null guard; single page → valid PDF magic bytes; multiple pages → valid PDF; merged size check |
| `OpenHtmlToPdfRendererTest` | 5 | Already existed from Phase 8 |
| `PublicationPipelineTest` | 17 | Already existed from Phase 7 |

### Engineering Notes

- `PDFMergerUtility` from PDFBox 2.0.24 is already on the compile classpath transitively via `openhtmltopdf-pdfbox-1.0.10.jar`, but the JPMS module `org.apache.pdfbox` must be explicitly required for the compiler to resolve its types.
- PDFBox is an automatic module (filename-based module name `org.apache.pdfbox`). The `requires org.apache.pdfbox;` directive in `module-info.java` enables it without any `pom.xml` change.
- `MemoryUsageSetting.setupMainMemoryOnly()` is appropriate for this use case — pages are rendered one at a time from a `ByteArrayInputStream`, so there is no need for temp-file buffering.
- Empty input returns `new byte[0]` rather than throwing. This matches the `runWithEmptyManifestShouldProduceEmptyArtifact` test in `PublicationPipelineTest`.
- The `SiteExporterCommand` now has four try/catch blocks for the four pipeline stages. Each stage independently exits on failure, which gives a clear error message per stage.

### Decisions

- Chose `PDFMergerUtility` over `PDDocument.importPage()` because `PDFMergerUtility` handles document-level metadata (AcroForm, bookmarks, page labels) and streams directly from `ByteArrayInputStream` without intermediate files.
- Did not create a separate `PdfExportService` — `PublicationPipeline.run()` already handles per-page rendering in a loop, making a dedicated service redundant.

### Tradeoffs

| Choice | Alternative | Reason |
|---|---|---|
| `MemoryUsageSetting.setupMainMemoryOnly()` | `setupTempFileOnly()` | Simpler; acceptable for small-to-medium sites that fit in heap |
| Empty → `byte[0]` | Throw `IllegalArgumentException` | Matches `PublicationPipeline` contract which writes zero bytes when no pages succeed |
| Four sequential try/catch blocks in `main()` | Single outer try/catch | Makes the failure point explicit; each stage has a distinct error message |

### Risks

- Large sites may cause OOM if all per-page PDFs are held in memory simultaneously before merging. Temp-file mode (`setupTempFileOnly()`) would mitigate this but adds I/O.
- `PDFMergerUtility` with `MemoryUsageSetting` is deprecated in PDFBox 3.x. If the project upgrades PDFBox, the merge API will need updating.

### Known Limitations

- No manifest-ordered page sequencing enforcement — pages are assembled in the order they appear in `MirrorManifest.pages()`, which is discovery order, not logical reading order.
- No per-page render failure recovery — if `OpenHtmlToPdfRenderer` throws for one page, the entire pipeline aborts.
- The CLI does not report the asset download or link rewrite step counts.

### Follow-ups

- Phase 10: `ManifestOrderPdfAssemblyStrategy` — explicit ordering by `discoveredOrder` or configurable comparator.
- Consider `setupTempFileOnly()` merge mode for large sites.
- Per-page render failure recovery: skip failed pages and continue rather than aborting.
- Replace `System.out.printf` calls in `PublicationPipeline` and `SiteExporterCommand` with a structured logger.

### Next Step

Phase 10 — PDF Assembly ordering: implement `ManifestOrderPdfAssemblyStrategy` that respects explicit page ordering, add assembly report, and handle the case where a rendered page is missing.

---
