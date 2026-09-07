---
type: Engineering Log Entry
title: "Phase 10.10 — Publication Driver Refactor"
description: "Canonical engineering evidence for Phase 10.10 — Publication Driver Refactor."
tags: [site-exporter, engineering-log, migration]
timestamp: 2026-07-05T00:00:00Z
ckf_version: "0.1"
ckf_status: completed
ckf_scope: site-exporter
ckf_owner: project
---
# Phase 10.10 — Publication Driver Refactor

### Summary

Removed format-specific `if/else` publication branching from `SiteExporterCommand` by introducing a `PublicationDriver` interface with `PdfPublicationDriver` and `MarkdownPublicationDriver` implementations, and a `PublicationDrivers` factory. The command now selects a driver by format and delegates both the asset-processing decision and the publication act to it. All PDF and Markdown internals are encapsulated inside their respective drivers. EPUB throws a clear `IllegalArgumentException` from the factory.

### Scope

**Included:**
- `PublicationDriver` interface (`requiresAssetProcessing()` + `publish()`)
- `PdfPublicationDriver` — encapsulates all PDF wiring (reader-pages, Pdf2HtmlExAwarePdfRenderer, PublicationPipeline)
- `MarkdownPublicationDriver` — encapsulates MarkdownPublicationWriter and markdown-pages
- `PublicationDrivers` factory — `forFormat(format, outputDir)` switch expression
- `SiteExporterCommand` — format-specific branches replaced with driver pattern
- 25 new tests across three test classes

**Excluded:**
- ePub implementation (EPUB throws at factory time)
- Changes to `PublicationPipeline`, `MarkdownPublicationWriter`, or any mirror/asset/link stage

### Deliverables

- `PublicationDriver.java` — new interface
- `PdfPublicationDriver.java` — new class
- `MarkdownPublicationDriver.java` — new class
- `PublicationDrivers.java` — new factory class
- `PublicationDriversTest.java` — 7 tests
- `PdfPublicationDriverTest.java` — 10 tests
- `MarkdownPublicationDriverTest.java` — 10 tests (+ 3 null-guard tests)
- `SiteExporterCommand.java` — format branching removed

### Changed Files

| File | Change |
|---|---|
| `codex-ir-app/src/main/java/codex/apps/siteexporter/PublicationDriver.java` | Created |
| `codex-ir-app/src/main/java/codex/apps/siteexporter/PdfPublicationDriver.java` | Created |
| `codex-ir-app/src/main/java/codex/apps/siteexporter/MarkdownPublicationDriver.java` | Created |
| `codex-ir-app/src/main/java/codex/apps/siteexporter/PublicationDrivers.java` | Created |
| `codex-ir-app/src/main/java/codex/apps/siteexporter/SiteExporterCommand.java` | Refactored publication phase |
| `codex-ir-app/src/test/java/codex/apps/siteexporter/PublicationDriversTest.java` | Created |
| `codex-ir-app/src/test/java/codex/apps/siteexporter/PdfPublicationDriverTest.java` | Created |
| `codex-ir-app/src/test/java/codex/apps/siteexporter/MarkdownPublicationDriverTest.java` | Created |

### Validation

```
mvn test -pl codex-ir-app
Tests run: 321, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

- All 296 tests from prior phases still pass ✓
- 25 new driver tests: factory returns correct types, EPUB throws clearly, `requiresAssetProcessing()` contracts, `publish()` produces correct artifacts and page counts ✓
- `SiteExporterCommand` no longer contains `if (format == MARKDOWN)` or any `Pdf2HtmlExAwarePdfRenderer` / `MarkdownPublicationWriter` construction ✓

### Tests

| Test class | Tests | Status |
|---|---|---|
| `PublicationDriversTest` | 7 | Added |
| `PdfPublicationDriverTest` | 9 | Added |
| `MarkdownPublicationDriverTest` | 9 | Added |

Key tests:
- `forFormatShouldReturnPdfDriverForPdf` — `assertInstanceOf(PdfPublicationDriver.class, ...)`
- `forFormatShouldReturnMarkdownDriverForMarkdown` — `assertInstanceOf(MarkdownPublicationDriver.class, ...)`
- `forFormatShouldThrowForEpub` — message contains "EPUB" and a supported alternative
- `pdfDriverShouldRequireAssetProcessing` — `requiresAssetProcessing()` returns `true`
- `markdownDriverShouldNotRequireAssetProcessing` — returns `false`
- `publishShouldWriteValidPdfToOutputPath` — output starts with `%PDF`
- `publishShouldWriteMarkdownFileToOutputPath` — output contains extracted text

### Engineering Notes

- `PublicationDriver.requiresAssetProcessing()` removes the last format-aware conditional from `SiteExporterCommand`. The command now reads: get driver → if `requiresAssetProcessing()` → run asset/link stages → `driver.publish(...)`. No format switch.
- `PublicationDrivers.forFormat()` uses a Java `switch` expression which is exhaustive over `PublicationFormat`; the compiler will emit a warning if a new format constant is added without updating the switch.
- Both drivers validate their `source` and `options` arguments with `Objects.requireNonNull` so callers get clear NPEs without having to trace into pipeline internals.
- `PdfPublicationDriver` derives `reader-pages/` from the constructor's `outputDir`, consistent with the pre-refactor behavior in `SiteExporterCommand`.

### Decisions

- **`requiresAssetProcessing()` on the driver interface rather than in the command**: the driver knows whether it needs processed assets (PDF = yes, Markdown = no). Keeping this inside the driver means the command is fully format-agnostic and future drivers can express their own requirements.
- **`IllegalArgumentException` for EPUB** (not a checked exception or enum-less approach): EPUB is a known, named unsupported case; `IllegalArgumentException` with a clear message is the right signal that the caller passed an unsupported value, not a recoverable I/O failure.
- **Concrete driver classes visible in the factory tests** via `assertInstanceOf`: this is a deliberate decision — the factory contract includes which concrete type is returned (it's part of the API surface), so the test documents and enforces it.

### Tradeoffs

- **Concrete driver classes exposed in test assertions**: tests now depend on the concrete class names `PdfPublicationDriver` and `MarkdownPublicationDriver`. If a driver is renamed or the factory is changed to return a wrapper, these tests break. This is acceptable — the break is intentional and the tests serve as a guard against accidental driver substitution.
- **`outputDir` required at factory time** rather than at `publish()` time: drivers need the output directory to construct their side-output paths (`reader-pages/`, `markdown-pages/`). Accepting it at construction keeps `publish()` signature simple and consistent with `PublicationExportOptions` (which already carries `outputPath`).

### Risks

- If a new `PublicationFormat` constant is added and `PublicationDrivers.forFormat()` is not updated, the Java switch expression will compile successfully (it still exhausts all cases at compile time only if sealed). At runtime it would throw `MatchException`. A future `assert false : "unreachable"` guard or tests for each format constant would make this safer.

### Known Limitations

- EPUB is entirely unsupported; requesting it fails fast at the factory with a clear message.
- The `SiteExporterCommand` still contains `--format` parsing logic that must be kept in sync with new `PublicationFormat` values.

### Follow-ups

1. Add an exhaustiveness test: `forFormatShouldHandleAllKnownFormats` — iterates `PublicationFormat.values()` and verifies either a driver is returned or an `IllegalArgumentException` is thrown for each known constant.
2. When EPUB is implemented, add `EpubPublicationDriver` and update the factory switch.
3. Consider making `PublicationDrivers` an interface (`PublicationDriverFactory`) to allow injection in integration tests.

### Next Step

Validate end-to-end with a real mirror using both `--format pdf` and `--format markdown` to confirm the driver selection works in production. Then provide a real pdf2htmlEX export sample to close the validation gap for the reader-extraction path.
