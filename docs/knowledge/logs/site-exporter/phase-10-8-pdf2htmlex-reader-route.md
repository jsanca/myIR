---
type: Engineering Log Entry
title: "Phase 10.8 — Wire pdf2htmlEX Reader Route"
description: "Canonical engineering evidence for Phase 10.8 — Wire pdf2htmlEX Reader Route."
tags: [site-exporter, engineering-log, migration]
timestamp: 2026-07-05T00:00:00Z
ckf_version: "0.1"
ckf_status: completed
ckf_scope: site-exporter
ckf_owner: project
---
# Phase 10.8 — Wire pdf2htmlEX Reader Route

### Summary

Wired the Phase 10.7 extraction pipeline into the production rendering path by introducing `Pdf2HtmlExAwarePdfRenderer`, a `PdfRenderer` decorator. The decorator intercepts each HTML file before it reaches `OpenHtmlToPdfRenderer`, detects pdf2htmlEX output, and transparently substitutes a clean reader HTML produced by `ReaderHtmlWriter`. Normal pages are forwarded to the inner renderer unchanged. `PublicationPipeline`, `PdfAssemblyStrategy`, and `PublicationOrderingStrategy` required no changes. `SiteExporterCommand` is updated to wrap `OpenHtmlToPdfRenderer` with the new decorator.

### Scope

**Included:**
- `Pdf2HtmlExAwarePdfRenderer` — new decorator class
- `SiteExporterCommand` — one-line change to wire the decorator
- 13 new tests covering null guards, normal-path delegation, reader-path routing, artifact existence, auto-directory creation, content validation, end-to-end PDF rendering, and a full mixed-manifest pipeline integration test

**Excluded:**
- Changes to `PublicationPipeline`, `PdfAssemblyStrategy`, or `PublicationOrderingStrategy`
- Failure-mode observability (e.g. recording whether each page was routed via reader path in `AssemblyReport`)
- Column-layout or image handling improvements to the extractor

### Deliverables

- `Pdf2HtmlExAwarePdfRenderer.java` — new class (57 source lines)
- `Pdf2HtmlExAwarePdfRendererTest.java` — 13 tests
- `SiteExporterCommand.java` — updated to use `Pdf2HtmlExAwarePdfRenderer`

### Changed Files

| File | Change |
|---|---|
| `codex-ir-app/src/main/java/codex/apps/siteexporter/Pdf2HtmlExAwarePdfRenderer.java` | Created |
| `codex-ir-app/src/main/java/codex/apps/siteexporter/SiteExporterCommand.java` | Updated renderer wiring |
| `codex-ir-app/src/test/java/codex/apps/siteexporter/Pdf2HtmlExAwarePdfRendererTest.java` | Created |

### Validation

```
mvn test -pl codex-ir-web,codex-ir-app
Tests run: 243, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Mixed-manifest pipeline test log (from `pipelineWithMixedManifestShouldRenderBothPagesAndCreateReaderArtifact`):
```
[Pipeline] Rendering .../content/normal.html
[Pipeline] Rendering .../content/pdf2htmlex.html
[Pdf2HtmlEx] pdf2htmlex.html → reader path (4 paragraphs across 1 page(s)) → pdf2htmlex.html.reader.html
[Pipeline] Artifact written → .../output.pdf (2136 bytes, 2/2 pages rendered, 0 failed)
```

- Normal page rendered via original path ✓
- pdf2htmlEX page routed through reader path ✓
- Reader HTML artifact exists at `reader-pages/pdf2htmlex.html.reader.html` ✓
- No reader HTML created for normal page ✓
- Output PDF starts with `%PDF` ✓
- Page ordering preserved (normal at position 0, pdf2htmlEX at position 1) ✓

### Tests

| Test | Status |
|---|---|
| `constructorShouldThrowWhenDelegateIsNull` | Added |
| `constructorShouldThrowWhenReaderPagesDirIsNull` | Added |
| `renderShouldThrowWhenHtmlFileIsNull` | Added |
| `renderShouldThrowWhenOptionsIsNull` | Added |
| `renderShouldDelegateDirectlyForNormalHtml` | Added |
| `renderShouldReturnDelegateResultForNormalHtml` | Added |
| `renderShouldRouteViaReaderPathForPdf2HtmlExPage` | Added |
| `renderShouldWriteReaderHtmlToReaderPagesDir` | Added |
| `renderShouldAutoCreateReaderPagesDirWhenAbsent` | Added |
| `readerHtmlShouldContainExtractedValidationStrings` | Added |
| `renderShouldProduceValidPdfBytesForPdf2HtmlExPage` | Added |
| `pipelineWithMixedManifestShouldRenderBothPagesAndCreateReaderArtifact` | Added |
| `pipelineOrderingShouldBePreservedAcrossMixedPages` | Added |

### Engineering Notes

- The HTML file is read twice on the **normal path**: once in `Pdf2HtmlExAwarePdfRenderer.render()` for detection, and once inside `OpenHtmlToPdfRenderer.render()` for XHTML conversion and rendering. This is acceptable for a typical mirror where most pages are not pdf2htmlEX output. If it becomes a bottleneck, `PdfRenderer` could be extended to accept a pre-read string.
- On the **pdf2htmlEX path**, the file is read only once; the same string feeds detection, extraction, and writer. The inner renderer reads only the newly written reader HTML.
- The `readerPagesDir` in `SiteExporterCommand` is fixed as `outputDir/reader-pages`, co-located with the mirror. This is predictable for debugging and post-processing.
- The log line `[Pdf2HtmlEx] <file> → reader path (<N> paragraphs across <P> page(s)) → <reader-file>` is emitted at `stdout` (matching the existing `[Pipeline]` log style) so operators can see which pages are rerouted without enabling verbose logging.

### Decisions

- **Decorator over modifying `PublicationPipeline`**: the pipeline's responsibility is sequencing, failure handling, and assembly — not format-specific routing. A decorator at the `PdfRenderer` level keeps those concerns separated and lets the routing be tested independently of the pipeline.
- **`readerPagesDir` as constructor argument**: lets callers (tests, CLI) provide a predictable path rather than deriving it dynamically from each HTML file's parent (which would scatter reader files across the mirror tree).
- **`PublicationPipeline` unchanged**: all existing pipeline tests pass unmodified; the new class is additive only.

### Tradeoffs

- **Double file read on normal path**: minor I/O overhead, cleanest API. Alternative (caching the string inside a custom `PdfRenderer` variant) would require changing the interface, which is not justified here.
- **`AssemblyReport` does not track reader-path count**: adding a `pagesRoutedViaReader` counter would be useful for observability but requires changing a record that is used in several places. Deferred to a follow-up.

### Risks

- If a non-pdf2htmlEX page happens to contain `#page-container` + `.pf` + `.t` in its markup, it would be incorrectly routed via the reader path, losing its original rendering. The detector uses three independent signals (meta, comment, structure) and requires the structural signal to have all three elements, which substantially reduces the false-positive rate.

### Known Limitations

- The reader HTML written to `reader-pages/` is not cleaned up after the pipeline completes. For large mirrors this can leave many small files on disk.
- Validation is based on a synthetic pdf2htmlEX fixture; a real Deep Learning Book export has not been tested.

### Follow-ups

1. Add `pagesRoutedViaReader` counter to `AssemblyReport` for observability.
2. Clean up or archive `reader-pages/` after successful pipeline runs.
3. Allow `readerPagesDir` to be configured via `SiteExporterCommand --reader-pages-dir <dir>`.
4. Validate against a real Deep Learning Book pdf2htmlEX export.

### Next Step

Validate against a real pdf2htmlEX export (Deep Learning Book or similar). Provide the sample HTML file and run the end-to-end pipeline to confirm the reader-path routing handles real-world coordinate CSS and produces a readable PDF.
