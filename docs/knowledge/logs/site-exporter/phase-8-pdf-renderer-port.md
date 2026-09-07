---
type: Engineering Log Entry
title: "Phase 8 — PDF Renderer Port"
description: "Canonical engineering evidence for Phase 8 — PDF Renderer Port."
tags: [site-exporter, engineering-log, migration]
timestamp: 2026-07-05T00:00:00Z
ckf_version: "0.1"
ckf_status: completed
ckf_scope: site-exporter
ckf_owner: project
---
# Phase 8 — PDF Renderer Port

### Summary

Introduced the PDF rendering port: `PdfRenderer` interface, `PdfRenderOptions` value record, `RenderedPdf` result record, and `OpenHtmlToPdfRenderer` — the live implementation backed by OpenHTMLToPDF / Apache PDFBox. The `PublicationPipeline.run()` method was updated to pass `PdfRenderOptions.forFile(htmlFile)` on each render call and extract bytes from the returned `RenderedPdf`. All fake renderer lambdas in `PublicationPipelineTest` were updated to match the new two-parameter signature.

### Scope

Included: `PdfRenderOptions`, `RenderedPdf`, `PdfRenderer` signature update, `OpenHtmlToPdfRenderer` implementation, `PublicationPipeline.run()` wiring, `PublicationPipelineTest` lambda fixes, `OpenHtmlToPdfRendererTest`.

Excluded: page-size selection from CLI, font embedding, CSS customisation, EPUB rendering, streamed output, progress callbacks.

### Deliverables

- `PdfRenderOptions.java` — record with `pageSize` and nullable `baseUri`; factory methods `defaults()` and `forFile(Path)`
- `RenderedPdf.java` — record wrapping rendered `byte[]` with defensive copy in compact constructor and `bytes()` accessor
- `OpenHtmlToPdfRenderer.java` — `PdfRenderer` implementation using `PdfRendererBuilder.useFastMode()`
- `PublicationPipelineTest.java` — all fake renderer lambdas updated to two-parameter signature
- `OpenHtmlToPdfRendererTest.java` — 5 integration/unit tests including `%PDF` magic-byte assertion

### Changed Files

| File | Change |
|------|--------|
| `codex-ir-app/pom.xml` | Added `openhtmltopdf-pdfbox` dependency (version managed in parent) |
| `pom.xml` (parent) | Added `openhtmltopdf-pdfbox:1.0.10` to `dependencyManagement` |
| `module-info.java` | `requires openhtmltopdf.pdfbox;` |
| `PdfRenderer.java` | Signature changed: `byte[] render(Path)` → `RenderedPdf render(Path, PdfRenderOptions)` |
| `PdfRenderOptions.java` | NEW |
| `RenderedPdf.java` | NEW |
| `OpenHtmlToPdfRenderer.java` | NEW |
| `PublicationPipeline.java` | `run()` updated to use new renderer signature |
| `PublicationPipelineTest.java` | All fake renderer lambdas updated |
| `OpenHtmlToPdfRendererTest.java` | NEW |

### Validation

- `mvn test` — 132 tests, 0 failures, 0 errors
- `OpenHtmlToPdfRendererTest.renderShouldProducePdfBytesStartingWithMagicHeader` confirmed `%PDF` magic bytes in output

### Tests

| Test class | Tests | Notes |
|------------|-------|-------|
| `OpenHtmlToPdfRendererTest` | 5 | integration (real PDF rendered in temp dir); magic-byte check; null guards; defensive-copy assertion |
| `PublicationPipelineTest` | updated | fake lambdas fixed to match new `PdfRenderer` signature |

### Engineering Notes

- `PdfRenderOptions.baseUri` is nullable because the `OpenHtmlToPdfRenderer` falls back to `htmlFile.toAbsolutePath().getParent().toUri()` when absent. Making it `Optional` was considered but rejected — an `Optional` field in a record used purely as a config bag adds noise without benefit.
- `RenderedPdf.bytes()` returns a clone both at construction (compact constructor) and at access (`bytes()` override). The double-copy ensures callers can mutate their copy without affecting the stored bytes, matching the documented contract.
- `openhtmltopdf.pdfbox` is the JPMS module name derived from the JAR filename. The library has no `Automatic-Module-Name` manifest entry. Verified with `jar --describe-module`.

### Decisions

- `useFastMode()` enabled unconditionally. It skips justification calculation and is appropriate for this use case. Can be made optional via `PdfRenderOptions` later.
- `OpenHtmlToPdfRenderer` is a concrete `public final class`, not hidden behind `PdfRenderers` factory, because there is currently only one implementation and the interface already provides the abstraction boundary.

### Tradeoffs

- Real PDF rendering in tests is slower than a fake (3.5 s for the renderer tests vs. milliseconds for pipeline tests). Accepted because the `%PDF` magic-byte check proves the integration actually works; a fake would give no coverage.
- Wrapping non-`IOException` exceptions from `PdfRendererBuilder.run()` in `new IOException(...)` loses the original exception type but keeps the `throws IOException` contract clean. The original exception is preserved as the cause.

### Risks

- OpenHTMLToPDF 1.0.10 supports only XHTML-compliant HTML. Real-world mirrored pages with quirks-mode HTML may fail at render time. This will surface during Phase 9 (end-to-end) testing.
- Font scanning at first PDFBox startup (`FileSystemFontProvider`) can take several seconds on CI environments with many installed fonts.

### Known Limitations

- Page size (`A4`) is passed through `PdfRenderOptions` but `OpenHtmlToPdfRenderer` does not currently forward it to `PdfRendererBuilder` — the library respects the `@page` CSS rule instead. This is intentional for now; explicit page-size API can be added if needed.
- No multi-threaded rendering; the pipeline processes pages sequentially.

### Follow-ups

- Forward `pageSize` to `PdfRendererBuilder` if CSS-level override is insufficient.
- Add per-page error handling so a single failing page does not abort the whole pipeline.
- Consider streaming assembly rather than accumulating all `byte[]` in memory for large sites.

### Next Step

Phase 9 — CLI wiring: connect `SiteExporterCommand` → `SiteMirrorService` → `SiteAssetService` → `SiteLinkRewriteService` → `PublicationPipeline` end-to-end, so `--url` + `--out-dir` produce a real PDF artifact.
