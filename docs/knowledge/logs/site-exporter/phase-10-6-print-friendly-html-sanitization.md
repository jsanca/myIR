---
type: Engineering Log Entry
title: "Phase 10.6 — Print-Friendly HTML Sanitization"
description: "Canonical engineering evidence for Phase 10.6 — Print-Friendly HTML Sanitization."
tags: [site-exporter, engineering-log, migration]
timestamp: 2026-07-05T00:00:00Z
ckf_version: "0.1"
ckf_status: completed
ckf_scope: site-exporter
ckf_owner: project
---
# Phase 10.6 — Print-Friendly HTML Sanitization

### Summary

Extended `HtmlToXhtmlSanitizer` with a `sanitizeForPrint` method that prepares HTML for PDF rendering by removing JavaScript and stripping inlined base64 font declarations — the two CSS-level patterns most likely to cause OpenHTMLToPDF to fail or produce oversized intermediate documents. `OpenHtmlToPdfRenderer` now uses this method instead of plain `sanitize`, gains an optional `debugDir` that captures the sanitized XHTML on render failure, and reports the root-cause exception message in its `IOException` rather than the previous opaque string.

### Scope

**Included:**
- `HtmlToXhtmlSanitizer.sanitizeForPrint` — removes `<script>` elements; strips `@font-face` blocks whose `src` uses a `data:` URI; removes the `<style>` element entirely if it becomes empty after stripping
- `OpenHtmlToPdfRenderer` — uses `sanitizeForPrint`; new `Path debugDir` constructor; debug XHTML written to `<debugDir>/<htmlFileName>.xhtml` on failure; improved error message includes root-cause text
- Tests for all new behaviour (20 sanitizer tests, 15 renderer tests)

**Excluded:**
- CSS url(data:…) rewriting for images
- Inlining external resources
- Crawler or manifest changes
- Playwright / headless rendering path

### Deliverables

- Updated `HtmlToXhtmlSanitizer.java` — `sanitizeForPrint` method + `FONT_FACE_DATA_URI` pattern + extracted `toXhtml` helper; `final` removed to allow test subclassing
- Updated `OpenHtmlToPdfRenderer.java` — `debugDir` field, `(Path)` constructor, `writeDebugXhtmlQuietly`, improved error message
- Updated `HtmlToXhtmlSanitizerTest.java` — 8 new tests for `sanitizeForPrint` (20 total)
- Updated `OpenHtmlToPdfRendererTest.java` — 5 new tests: base64 font-face, inline scripts, failure message, debug XHTML write, no debug on success (15 total)

### Changed Files

| File | Change |
|---|---|
| `codex-ir-app/src/main/java/codex/apps/siteexporter/HtmlToXhtmlSanitizer.java` | Extended |
| `codex-ir-app/src/main/java/codex/apps/siteexporter/OpenHtmlToPdfRenderer.java` | Extended |
| `codex-ir-app/src/test/java/codex/apps/siteexporter/HtmlToXhtmlSanitizerTest.java` | Extended |
| `codex-ir-app/src/test/java/codex/apps/siteexporter/OpenHtmlToPdfRendererTest.java` | Extended |

### Validation

```
mvn test -pl codex-ir-web,codex-ir-app
Tests run: 187, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

- `HtmlToXhtmlSanitizerTest` — 20 tests, 0 failures
- `OpenHtmlToPdfRendererTest` — 15 tests, 0 failures
- `renderShouldProducePdfFromHtmlWithInlinedBase64FontFace` confirms a 2 KB simulated WOFF2 base64 block is stripped and PDF renders correctly
- `renderShouldWriteDebugXhtmlWhenRenderFails` confirms the debug XHTML is written and the debug dir is auto-created
- `renderShouldNotWriteDebugXhtmlOnSuccess` confirms the debug dir is never created on successful renders

### Tests

| Test | Class | Status |
|---|---|---|
| `sanitizeForPrintShouldThrowWhenHtmlIsNull` | `HtmlToXhtmlSanitizerTest` | Added |
| `sanitizeForPrintShouldThrowWhenBaseUriIsNull` | `HtmlToXhtmlSanitizerTest` | Added |
| `sanitizeForPrintShouldRemoveScriptElements` | `HtmlToXhtmlSanitizerTest` | Added |
| `sanitizeForPrintShouldStripFontFaceBlockWithDataUri` | `HtmlToXhtmlSanitizerTest` | Added |
| `sanitizeForPrintShouldRemoveEntireStyleBlockWhenOnlyFontFaceDataRemains` | `HtmlToXhtmlSanitizerTest` | Added |
| `sanitizeForPrintShouldPreserveNonDataUriFontFace` | `HtmlToXhtmlSanitizerTest` | Added |
| `sanitizeForPrintShouldPreserveSemanticContentElements` | `HtmlToXhtmlSanitizerTest` | Added |
| `sanitizeForPrintShouldProduceValidXhtmlWithSelfClosedVoidElements` | `HtmlToXhtmlSanitizerTest` | Added |
| `renderShouldProducePdfFromHtmlWithInlinedBase64FontFace` | `OpenHtmlToPdfRendererTest` | Added |
| `renderShouldProducePdfFromHtmlWithInlineScripts` | `OpenHtmlToPdfRendererTest` | Added |
| `renderFailureMessageShouldIncludeFilePathAndRootCause` | `OpenHtmlToPdfRendererTest` | Added |
| `renderShouldWriteDebugXhtmlWhenRenderFails` | `OpenHtmlToPdfRendererTest` | Added |
| `renderShouldNotWriteDebugXhtmlOnSuccess` | `OpenHtmlToPdfRendererTest` | Added |

### Engineering Notes

- `@font-face` detection uses `[^}]*data:[^}]*` which is safe because CSS `@font-face` blocks never contain nested braces and base64 characters (`[A-Za-z0-9+/=]`) do not include `}`.
- `writeDebugXhtmlQuietly` silently swallows any write error so a filesystem problem never masks the original render failure.
- `HtmlToXhtmlSanitizer` has `final` removed solely to allow anonymous subclassing in tests (the broken-sanitizer pattern for forcing a render failure). The class is not designed as an extension point.
- `toXhtml(Document)` is extracted as a private static helper shared by both `sanitize` and `sanitizeForPrint`.

### Decisions

- `sanitizeForPrint` is a separate method rather than a flag on `sanitize` — two distinct concerns with different contracts, and the existing `sanitize` signature is used in tests that expect no stripping.
- Debug dir is a constructor argument on `OpenHtmlToPdfRenderer` rather than a field on `PdfRenderOptions` — keeps the options record small and avoids a breaking change to the two-arg constructor used in existing tests.
- Debug dir is created automatically (`Files.createDirectories`) so callers need not pre-create it.

### Tradeoffs

- **Regex vs. CSS parser for @font-face stripping:** Regex is fragile if a `@font-face` block were to contain `}` inside a string value. In practice no CSS property value includes a literal `}` outside of a string, and adding a full CSS parser would require a new dependency (disallowed by project rules). Regex is acceptable here.
- **Removing all `<script>` vs. only risky scripts:** Simpler and safer for PDF rendering, where JavaScript execution is meaningless. No content lost.

### Risks

- Regex `[^}]*data:[^}]*` will incorrectly strip a `@font-face` block if a property value legitimately contained the substring `data:` without being a data URI (e.g. a comment). This is an extremely unlikely edge case in real-world CSS.
- If `debugDir` points to a location without write permission, the debug file is silently skipped. No user-visible warning is emitted.

### Known Limitations

- CSS `url(data:…)` in `background-image` or other properties is not stripped — only `@font-face src:` data URIs are removed. Real-world pages may include large base64 images in CSS; those are left for a future phase.
- External CSS files (via `<link rel="stylesheet">`) are not fetched or filtered; only inline `<style>` blocks are processed.

### Follow-ups

1. Strip base64 `url(data:…)` in CSS properties beyond `@font-face` (e.g. `background-image`).
2. Add a page-level render timeout so a single malformed page can't block the pipeline indefinitely.
3. Emit a warning log line when `writeDebugXhtmlQuietly` catches an IOException (currently fully silent).
4. Consider adding a `PdfRenderDiagnostics` record to surface per-page render outcomes (success, skipped, failed + cause) for the assembly report.

### Next Step

Integrate the debug-dir option into `ManifestOrderPdfAssemblyStrategy` so that failed pages surface a debug XHTML under `<outputDir>/render-debug/` automatically during a full site export run.
