# Site Exporter — Engineering Log

## Phase 10.10 — Publication Driver Refactor

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

## Phase 10.9 — Markdown Publication Writer

### Summary

Added Markdown as a first-class publication format. `MarkdownPublicationWriter` produces a single combined `.md` file from all ordered mirror pages: pdf2htmlEX pages are routed through the existing `Pdf2HtmlExTextExtractor` pipeline; normal HTML pages are processed via a Jsoup paragraph extractor. Optional per-page `.md` files are written under `markdown-pages/`. `SiteExporterCommand` supports `--format markdown` with a format-dependent default output path (`./output.md`) and skips asset download / link rewriting in Markdown mode since neither is needed for text extraction.

### Scope

**Included:**
- `MarkdownPublicationWriter` — new class with builder; produces combined `.md` and optional per-page files
- `PublicationFormat.MARKDOWN` — new enum value
- `SiteExporterCommand` — MARKDOWN branch in `main()`, format-dependent default output path, skip asset/link phases for Markdown
- 33 new tests in `MarkdownPublicationWriterTest`, 2 new tests in `SiteExporterCommandTest`

**Excluded:**
- ePub support (deferred per constraints)
- Changes to `PublicationPipeline` (PDF path untouched)
- `NormalHtmlTextExtractor` abstraction (normal HTML extraction is inlined in `MarkdownPublicationWriter`)
- Table/image extraction from pdf2htmlEX pages

### Deliverables

- `MarkdownPublicationWriter.java` — new class (200 source lines)
- `MarkdownPublicationWriterTest.java` — 33 tests
- `PublicationFormat.java` — `MARKDOWN` constant added
- `SiteExporterCommand.java` — MARKDOWN handling, format-aware defaults

### Changed Files

| File | Change |
|---|---|
| `codex-ir-app/src/main/java/codex/apps/siteexporter/MarkdownPublicationWriter.java` | Created |
| `codex-ir-app/src/main/java/codex/apps/siteexporter/PublicationFormat.java` | `MARKDOWN` added |
| `codex-ir-app/src/main/java/codex/apps/siteexporter/SiteExporterCommand.java` | MARKDOWN branch, format-aware default path, skip asset/link stages |
| `codex-ir-app/src/test/java/codex/apps/siteexporter/MarkdownPublicationWriterTest.java` | Created |
| `codex-ir-app/src/test/java/codex/apps/siteexporter/SiteExporterCommandTest.java` | 2 new tests |

### Validation

```
mvn test -pl codex-ir-app
Tests run: 296, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

- `writeShouldExtractPdf2HtmlExContentContainingExpectedStrings` confirms the synthetic fixture produces "Part I", "Applied Math and Machine Learning Basics", "This part of the book introduces..." ✓
- `writeShouldNotContainCssOrScriptFromPdf2HtmlExPage` confirms no `@font-face`, `<script>`, or `position:absolute` in output ✓
- `writeShouldPreservePublicationOrder` confirms `discoveredOrder` ordering is respected ✓
- Escaping unit tests confirm `\\`, `\[`, `\]`, and leading `\##` are emitted correctly ✓
- Per-page file tests confirm `markdown-pages/page.html.md` is created when configured ✓

### Tests

| Test | Status |
|---|---|
| `builderShouldThrowWhenSourceIsNull` | Added |
| `writeShouldThrowWhenOutputIsNull` | Added |
| `writeShouldCreateOutputFile` | Added |
| `writeShouldCreateOutputParentDirectoriesIfAbsent` | Added |
| `writeShouldStartWithLevelOneHeadingContainingSeedHost` | Added |
| `writeShouldIncludeDocumentLevelSourceComment` | Added |
| `writeShouldIncludeSectionHorizontalRuleForEachPage` | Added |
| `writeShouldDeriveSectionHeadingFromPageUrlPath` | Added |
| `writeShouldIncludePerPageSourceComment` | Added |
| `writeShouldExtractParagraphTextFromNormalHtml` | Added |
| `writeShouldExtractHeadingTextFromNormalHtml` | Added |
| `writeShouldExtractListItemTextFromNormalHtml` | Added |
| `writeShouldExtractPdf2HtmlExContentContainingExpectedStrings` | Added |
| `writeShouldNotContainCssOrScriptFromPdf2HtmlExPage` | Added |
| `writeShouldPreservePublicationOrder` | Added |
| `writeShouldReturnCorrectPageCount` | Added |
| `writeShouldReturnMarkdownFormat` | Added |
| `writeShouldReturnCorrectOutputPath` | Added |
| `writeShouldReturnPositiveSizeBytes` | Added |
| `writeShouldCreatePerPageMarkdownFilesWhenDirConfigured` | Added |
| `writeShouldNotCreateMarkdownPagesDirWhenNotConfigured` | Added |
| `perPageMarkdownFileShouldContainSameContentAsSection` | Added |
| `writeShouldEscapeBackslashesInExtractedText` | Added |
| `writeShouldEscapeBracketsInExtractedText` | Added |
| `writeShouldEscapeLeadingHashesInExtractedText` | Added |
| `writeShouldRecordFailureWhenHtmlFileIsMissing` | Added |
| `writeShouldSucceedForSuccessfulPagesEvenIfSomePagesMissing` | Added |
| `writeShouldHandleEmptyManifestGracefully` | Added |
| `escapeMarkdownShouldReturnEmptyForNullOrEmpty` | Added |
| `escapeMarkdownShouldEscapeBackslash` | Added |
| `escapeMarkdownShouldEscapeBrackets` | Added |
| `escapeMarkdownShouldEscapeLeadingHashAtLineStart` | Added |
| `escapeMarkdownShouldNotEscapeHashInMiddleOfLine` | Added |
| `parseArgsShouldDefaultToMarkdownOutputPathForMarkdownFormat` | Added |
| `parseArgsShouldUseExplicitOutputPathEvenForMarkdownFormat` | Added |

### Engineering Notes

- `MarkdownPublicationWriter` reuses `Pdf2HtmlExDetector` and `Pdf2HtmlExTextExtractor` directly rather than going through `Pdf2HtmlExAwarePdfRenderer`, since the Markdown path does not need an intermediate HTML rendering step.
- Normal HTML extraction selects `h1–h6`, `p`, and `li` elements via Jsoup. If none of these are present, it falls back to `body.text()`. This handles both richly structured web pages and minimal mirror captures.
- `escapeMarkdown` is package-visible (`static`) so it can be unit-tested directly without constructing a full writer.
- The document-level title is derived from the seed URL's host name (e.g. `book.example.com`), not from an HTML `<title>` tag, since no single page owns the publication title.
- Asset download and link rewriting are skipped in Markdown mode in `SiteExporterCommand` — these stages modify HTML files on disk and are unnecessary for text-only output.

### Decisions

- **`MarkdownPublicationWriter` rather than modifying `PublicationPipeline`**: `PublicationPipeline` is bound to the PDF contract (`PdfRenderer`, `PdfAssemblyStrategy`). Introducing a parallel writer class keeps the existing pipeline untouched and avoids forcing Markdown into an ill-fitting byte-array assembly model.
- **`ReaderDocument` as the shared intermediate for pdf2htmlEX pages**: the phase design note suggested this path and it was already available. Normal HTML does not produce a `ReaderDocument` — its extraction is simpler and inline.
- **Markdown escaping scope**: only `\`, `[`, `]`, and leading `#` sequences are escaped. Prose text from books and web pages rarely contains characters that would create unintended Markdown structure beyond these. Over-escaping (e.g. `*`, `_`) would make the output less readable.
- **Format-dependent default output path** (`./output.md` vs `./output.pdf`): resolved post-loop so both `--format` and `--output` can appear in any order in the CLI args.

### Tradeoffs

- **Normal HTML extraction does not preserve heading hierarchy**: `<h2>` text is extracted as a plain paragraph, not as `## heading` in the Markdown. This avoids conflicting with the `## section-name` structure used for per-page headings, but loses structural information. A future pass could map `<h1>` → `###` etc. with proper offset, but that's out of scope.
- **No deduplication of `<li>` items that are descendants of extracted `<p>` elements**: if an HTML page uses `<p>` inside `<li>`, both the `<li>` and `<p>` text will appear. This is rare in mirrored book content but could produce duplicates on heavily nested pages.

### Risks

- The Markdown escaping regex `(?m)^(#{1,6})(\s)` will not escape a line that begins with `#` immediately followed by a non-space (e.g. `#tag`). This is intentional — only valid Markdown heading syntax (space after `#`) is escaped. A line like `#tag` is safe and common in social-media scraped content.

### Known Limitations

- Validation is based on the synthetic pdf2htmlEX fixture; a real Deep Learning Book export has not been tested.
- Normal HTML extraction loses heading hierarchy and does not produce structured Markdown headings inside the section.
- Images are not included in Markdown output.

### Follow-ups

1. Validate against a real Deep Learning Book pdf2htmlEX export.
2. Add `--markdown-pages-dir` CLI flag so operators can configure the per-page directory path.
3. Consider mapping HTML heading levels (`h1`→`###`, `h2`→`####`) inside section content.
4. Add a `NormalHtmlTextExtractor` when normal-HTML extraction is needed in more than one context.

### Next Step

Validate Markdown output against a real mirror: run with `--from-mirror <path> --format markdown` on an existing mirror and verify the output is readable and contains expected content. Then provide a real pdf2htmlEX export sample to close the validation gap.

## Phase 10.8 — Resume From Existing Mirror (`--from-mirror`)

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

## Phase 10.8 — Wire pdf2htmlEX Reader Route

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

## Phase 10.7 — pdf2htmlEX Reader Extraction

### Summary

Introduced a four-class pipeline that handles mirrored pages generated by pdf2htmlEX. Instead of trying to render the original positioned HTML (which fails because of absolute-positioned spans, embedded fonts, and CSS transforms), the pipeline extracts the visible text content, reconstructs reading order, and produces a clean reader HTML that OpenHTMLToPDF can render without overlap or layout errors.

### Scope

**Included:**
- `Pdf2HtmlExDetector` — three independent detection signals (meta generator, HTML comment, structural fingerprint)
- `ReaderPage` and `ReaderDocument` records — structured extracted text
- `Pdf2HtmlExTextExtractor` — CSS coordinate parsing, span sorting, line and paragraph grouping
- `ReaderHtmlWriter` — produces clean HTML with page breaks, no scripts, no @font-face
- Test fixture `deeplearning_part1.html` — synthetic pdf2htmlEX page containing the three required validation strings
- 43 new tests across three test classes

**Excluded:**
- Integration into `PublicationPipeline` or `ManifestOrderPdfAssemblyStrategy` (deferred to a follow-up phase)
- Image extraction from pdf2htmlEX pages
- Table-structure preservation
- Validation against a real Deep Learning Book pdf2htmlEX export (pending user-provided sample)

### Deliverables

- `Pdf2HtmlExDetector.java` — new class
- `Pdf2HtmlExTextExtractor.java` — new class
- `ReaderDocument.java` — new record
- `ReaderPage.java` — new record
- `ReaderHtmlWriter.java` — new class
- `Pdf2HtmlExDetectorTest.java` — 11 tests
- `Pdf2HtmlExTextExtractorTest.java` — 19 tests
- `ReaderHtmlWriterTest.java` — 13 tests
- `src/test/resources/fixtures/pdf2htmlex/deeplearning_part1.html` — test fixture

### Changed Files

| File | Change |
|---|---|
| `codex-ir-app/src/main/java/codex/apps/siteexporter/Pdf2HtmlExDetector.java` | Created |
| `codex-ir-app/src/main/java/codex/apps/siteexporter/Pdf2HtmlExTextExtractor.java` | Created |
| `codex-ir-app/src/main/java/codex/apps/siteexporter/ReaderDocument.java` | Created |
| `codex-ir-app/src/main/java/codex/apps/siteexporter/ReaderPage.java` | Created |
| `codex-ir-app/src/main/java/codex/apps/siteexporter/ReaderHtmlWriter.java` | Created |
| `codex-ir-app/src/test/java/codex/apps/siteexporter/Pdf2HtmlExDetectorTest.java` | Created |
| `codex-ir-app/src/test/java/codex/apps/siteexporter/Pdf2HtmlExTextExtractorTest.java` | Created |
| `codex-ir-app/src/test/java/codex/apps/siteexporter/ReaderHtmlWriterTest.java` | Created |
| `codex-ir-app/src/test/resources/fixtures/pdf2htmlex/deeplearning_part1.html` | Created |

### Validation

```
mvn test -pl codex-ir-web,codex-ir-app
Tests run: 230, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

- `Pdf2HtmlExDetectorTest` — 11 tests, 0 failures
- `Pdf2HtmlExTextExtractorTest` — 19 tests, 0 failures
- `ReaderHtmlWriterTest` — 13 tests, 0 failures
- `writerOutputShouldRenderToValidPdfViaOpenHtmlToPdf` confirms the full extract→write→render path produces a valid PDF with `%PDF` magic bytes

Extracted text validation (from fixture):
- `"Part I"` ✓
- `"Applied Math and Machine Learning Basics"` ✓
- `"This part of the book introduces"` ✓

Generated reader HTML renders without overlapping text: confirmed by the successful OpenHTMLToPDF render test.

**Note:** Validation against a real Deep Learning Book pdf2htmlEX export is pending. Provide the sample file to add it as an additional test fixture.

### Tests

| Test | Class | Status |
|---|---|---|
| `detectDocumentShouldThrowWhenDocumentIsNull` | `Pdf2HtmlExDetectorTest` | Added |
| `detectShouldReturnTrueWhenMetaGeneratorContainsPdf2HtmlEx` | `Pdf2HtmlExDetectorTest` | Added |
| `detectShouldReturnTrueWhenMetaGeneratorIsCaseInsensitive` | `Pdf2HtmlExDetectorTest` | Added |
| `detectShouldReturnTrueWhenCreatedByCommentInBody` | `Pdf2HtmlExDetectorTest` | Added |
| `detectShouldReturnTrueWhenStructuralFingerprintPresent` | `Pdf2HtmlExDetectorTest` | Added |
| `detectShouldReturnTrueForFixtureFile` | `Pdf2HtmlExDetectorTest` | Added |
| `detectShouldReturnFalseForRegularHtmlPage` | `Pdf2HtmlExDetectorTest` | Added |
| `detectShouldReturnFalseWhenOnlyPageContainerIsPresentWithoutPfAndT` | `Pdf2HtmlExDetectorTest` | Added |
| `detectShouldReturnFalseForEmptyDocument` | `Pdf2HtmlExDetectorTest` | Added |
| (+ 2 null guard tests) | `Pdf2HtmlExDetectorTest` | Added |
| `extractShouldReturnEmptyDocumentWhenNoPfDivsPresent` | `Pdf2HtmlExTextExtractorTest` | Added |
| `extractShouldUseDocumentTitleAsPresentInHead` | `Pdf2HtmlExTextExtractorTest` | Added |
| `extractShouldProduceOneReaderPagePerPfDiv` | `Pdf2HtmlExTextExtractorTest` | Added |
| `extractShouldUseDataPageNoAttributeWhenPresent` | `Pdf2HtmlExTextExtractorTest` | Added |
| `extractShouldPreserveDocumentOrderWhenNoCssCoordinatesArePresent` | `Pdf2HtmlExTextExtractorTest` | Added |
| `extractFromFixtureShouldContainPartI` | `Pdf2HtmlExTextExtractorTest` | Added |
| `extractFromFixtureShouldContainSubtitle` | `Pdf2HtmlExTextExtractorTest` | Added |
| `extractFromFixtureShouldContainBodyTextPrefix` | `Pdf2HtmlExTextExtractorTest` | Added |
| `extractFromFixtureShouldSortPartIBeforeSubtitle` | `Pdf2HtmlExTextExtractorTest` | Added |
| `extractFromFixtureShouldJoinBodyLinesToSingleParagraph` | `Pdf2HtmlExTextExtractorTest` | Added |
| (+ 9 more unit tests) | `Pdf2HtmlExTextExtractorTest` | Added |
| `writeShouldProduceOnePageDivPerReaderPage` | `ReaderHtmlWriterTest` | Added |
| `writeShouldWrapEachParagraphInPTag` | `ReaderHtmlWriterTest` | Added |
| `writeShouldEscapeAmpersandsInParagraphText` | `ReaderHtmlWriterTest` | Added |
| `writeShouldContainNoScriptElements` | `ReaderHtmlWriterTest` | Added |
| `writeShouldContainNoAtFontFaceDeclarations` | `ReaderHtmlWriterTest` | Added |
| `writerOutputShouldRenderToValidPdfViaOpenHtmlToPdf` | `ReaderHtmlWriterTest` | Added |
| `writerOutputShouldContainAllValidationStrings` | `ReaderHtmlWriterTest` | Added |
| (+ 6 more unit tests) | `ReaderHtmlWriterTest` | Added |

### Engineering Notes

- CSS coordinates in pdf2htmlEX use class names like `.x0`, `.y3`, `.h1` mapped in the `<style>` block. The pattern `\.(x|y|h)(\d+)\s*\{\s*(left|bottom|top|height)\s*:\s*(-?[\d.]+)px` is robust enough for both compact (no spaces) and expanded CSS formats.
- Bottom-based y coordinates (the pdf2htmlEX default): higher `bottom:` value = closer to the top of the page. Sorted descending to achieve top-to-bottom reading order.
- Line grouping uses a 2 px tolerance (spans whose y values differ by < 2 px are on the same text line). In practice pdf2htmlEX generates identical y values for co-linear spans.
- Paragraph grouping uses a 1.5× average-line-height threshold. For the fixture this correctly separates the heading, subtitle, and body paragraph, while joining the four continuation lines into one paragraph.
- `Pdf2HtmlExDetector` uses three independent signals so that pages with only structural markup (no meta tag) are still correctly identified.
- `ReaderHtmlWriter` escapes `&`, `<`, `>` in all user text to prevent broken HTML. The `mdash` page-number separator is output as `&mdash;` (named entity), which OpenHTMLToPDF supports.

### Decisions

- **Synthetic test fixture over real sample**: no real pdf2htmlEX file was available at the time of implementation. The fixture is designed to match the exact CSS coordinate format that pdf2htmlEX uses, with coordinate values that exercise the line-grouping and paragraph-grouping logic precisely.
- **No integration into `PublicationPipeline` yet**: the four classes are self-contained and independently testable. Wiring them into the pipeline is a separate, well-defined follow-up.
- **`Pdf2HtmlExTextExtractor.extract(Document)` takes a Jsoup Document** to avoid double-parsing in callers that already hold a parsed document (e.g., the detector).

### Tradeoffs

- **Regex CSS parsing vs. a full CSS parser:** A full CSS parser would handle edge cases like `@charset`, nested `@media` blocks, and multi-value shorthand properties. In practice, pdf2htmlEX generates highly predictable CSS with one coordinate rule per class; the regex is sufficient and avoids a new dependency.
- **Gap threshold of 1.5× vs. fixed px value:** Adapts to the document's actual line height rather than assuming a specific font size. Performs correctly for both 9pt footnotes and 24pt headings in the fixture.

### Risks

- **Coordinate class name conflicts**: if a page's CSS reuses `.x0`, `.y0`, `.h0` class names for non-pdf2htmlEX purposes (unlikely given how specific this pattern is), coordinates could be misread.
- **Horizontal text runs**: the extractor joins spans on the same line with a single space. For languages without spaces (CJK), this is incorrect. Not a concern for the Deep Learning Book use case.
- **Multi-column layouts**: column text would be sorted left-to-right across the full page width, mixing the two columns. A more advanced implementation would need column detection.

### Known Limitations

- Real pdf2htmlEX files have not been tested. The synthetic fixture exercises the logic but does not guarantee correctness against actual exported Deep Learning Book chapters.
- Images embedded in pdf2htmlEX pages (base64 `<img>` elements) are silently dropped. Only text spans with class `.t` are extracted.
- Table structure is not preserved; table cells are extracted as sequential text spans.

### Follow-ups

1. Wire `Pdf2HtmlExDetector` + `Pdf2HtmlExTextExtractor` + `ReaderHtmlWriter` into the `PublicationPipeline` as a pre-render step: detect pdf2htmlEX pages and rewrite them to reader HTML before passing to `PdfRenderer`.
2. Validate the extractor against a real Deep Learning Book pdf2htmlEX export once the user provides the sample.
3. Add a two-column detection heuristic: if spans appear in two distinct x-bands, process each column independently.
4. Consider caching the `CssCoordinates` parse result when extracting many pages from the same document (currently re-parsed per call, but all pages share the same style blocks).

### Next Step

Integrate `Pdf2HtmlExDetector` and the extraction pipeline into `PublicationPipeline.run()` so that pdf2htmlEX pages are automatically rewritten to reader HTML before rendering, bypassing the problematic original layout.

## Phase 10.6 — Print-Friendly HTML Sanitization

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

## Phase 10.5 — HTML Sanitization Before PDF Rendering

### Summary

Introduced `HtmlToXhtmlSanitizer` to convert raw mirrored HTML to well-formed XHTML before handing it to OpenHTMLToPDF, which requires XML-compliant input. Without this step, real-world pages with bare `<br>`, `<meta>`, unclosed `<p>` or `<img>` tags fail to render. `OpenHtmlToPdfRenderer` now reads the file, sanitizes it via Jsoup XML output mode, then passes the clean XHTML string to `PdfRendererBuilder.withHtmlContent()`.

### Scope

**Included:**
- `HtmlToXhtmlSanitizer` — Jsoup parse + XML/XHTML output settings
- `OpenHtmlToPdfRenderer` — now sanitizes before rendering; accepts injected sanitizer for tests
- Tests for sanitizer (12) and renderer with malformed HTML (5 new)

**Excluded:**
- Crawler changes
- Manifest changes
- Assembly changes
- Playwright
- CSS url(...) rewriting
- Inlining external resources

### Deliverables

- `HtmlToXhtmlSanitizer.java` — new class
- `HtmlToXhtmlSanitizerTest.java` — 12 tests
- Updated `OpenHtmlToPdfRenderer.java` — sanitizer field + package-private constructor for injection
- Updated `OpenHtmlToPdfRendererTest.java` — 5 new malformed-HTML round-trip tests

### Changed Files

| File | Change |
|---|---|
| `codex-ir-app/src/main/java/codex/apps/siteexporter/HtmlToXhtmlSanitizer.java` | Created |
| `codex-ir-app/src/test/java/codex/apps/siteexporter/HtmlToXhtmlSanitizerTest.java` | Created |
| `codex-ir-app/src/main/java/codex/apps/siteexporter/OpenHtmlToPdfRenderer.java` | Updated |
| `codex-ir-app/src/test/java/codex/apps/siteexporter/OpenHtmlToPdfRendererTest.java` | Updated |

### Validation

```
mvn test -pl codex-ir-web,codex-ir-app
Tests run: 174, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

- `HtmlToXhtmlSanitizerTest` — 12 tests, 0 failures
- `OpenHtmlToPdfRendererTest` — 10 tests (was 5; added 5 malformed-HTML tests), 0 failures

### Tests

| Test class | Tests | What is covered |
|---|---|---|
| `HtmlToXhtmlSanitizerTest` | 12 | Null guards; bare `<br>`; bare `<meta charset>`; bare `<img>`; bare `<link>`; unclosed `<p>` tags; leading whitespace before doctype; leading HTML comments; missing html/head/body structure; text content preserved |
| `OpenHtmlToPdfRendererTest` (new) | 5 | Bare `<meta charset>`; bare `<br>` tags; unclosed `<p>` tags; leading whitespace + doctype; mixed void elements — all produce `%PDF` output |

### Engineering Notes

- Jsoup `Document.OutputSettings.Syntax.xml` makes Jsoup serialize void elements as self-closing (`<br />`, `<meta ... />`) and ensures all non-void elements are explicitly closed. This is exactly what OpenHTMLToPDF expects.
- `escapeMode(Entities.EscapeMode.xhtml)` ensures HTML entities are output in XHTML-compatible form.
- `charset(StandardCharsets.UTF_8)` keeps the output UTF-8 regardless of platform default.
- The `baseUri` is passed to `Jsoup.parse()` for in-document link resolution during parsing. It is not written into the output string; it is passed separately to `withHtmlContent()` for OpenHTMLToPDF asset resolution.
- `OpenHtmlToPdfRenderer` gains a package-private constructor `OpenHtmlToPdfRenderer(HtmlToXhtmlSanitizer)` to allow injection in tests without changing the public API.
- One test needed a fix during development: the original `sanitizeShouldSelfCloseImgElement` checked `!result.contains("<img src")`, which is always false because `<img src="..." />` also starts with `<img src`. Fixed to check `!result.contains("alt=\"logo\">")` (the bare closing `>` after attributes).

### Decisions

- Used Jsoup rather than a DOM/SAX transformer. Jsoup is already on the classpath and handles real-world broken HTML far better than standard XML parsers.
- Sanitizer is injected into `OpenHtmlToPdfRenderer` via a package-private constructor rather than making `HtmlToXhtmlSanitizer` an interface. The sanitizer has one correct implementation and the injection point is only for tests, so the full Interface+Factory pattern is not warranted.

### Tradeoffs

| Choice | Alternative | Reason |
|---|---|---|
| Jsoup XML output mode | Manual regex cleanup | Jsoup handles all the edge cases (implicit closing, entity encoding, attribute quoting) correctly |
| Package-private constructor for test injection | Public constructor or mocking | Avoids leaking test-only API surface while still allowing injection |
| Pass `baseUri` to both Jsoup and OpenHTMLToPDF | Pass only to OpenHTMLToPDF | Gives Jsoup accurate context for relative URL normalization during parsing |

### Risks

- Jsoup's HTML5 parser may alter some elements (e.g., move `<style>` from body to head, fix table structure). For PDF output this is generally harmless, but it means the XHTML passed to OpenHTMLToPDF may differ structurally from the original HTML.
- Very large HTML files will be held as a string in memory twice (raw + XHTML). This is acceptable for typical mirrored pages.

### Known Limitations

- Inline CSS `url(...)` references remain unmodified — relative asset paths inside stylesheets still depend on the `baseUri` being set correctly.
- External stylesheets referenced by relative `href` are resolved by OpenHTMLToPDF using the `baseUri`, which points to the local mirror directory. This works only if the assets were downloaded in Phase 6A.

### Follow-ups

- Phase 11: review `PublicationFormat` abstraction for ePub readiness.
- Consider adding a charset-detection step before sanitization for pages that declare a non-UTF-8 charset.
- Log sanitizer warnings (e.g., Jsoup parse error count) into the `AssemblyReport`.

### Next Step

Phase 11 — Future Format Readiness: review `PublicationFormat` abstraction, confirm no PDF-only concepts leak into the generic pipeline, and document the ePub extension point.

---

## Phase 10 — PDF Assembly

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

## Phase 9.5 — Publication Ordering Strategy

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

## Phase 9 — CLI Wiring and PDF Assembly

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

## Phase 8 — PDF Renderer Port

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

## Phase 7.5 — API Hygiene

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

## Phase 7 — Publication Pipeline Builder

### Summary

Added the programmatic pipeline composition API. `PublicationPipeline.builder()` wires a `PublicationSource`, a `PdfRenderer`, a `PdfAssemblyStrategy`, and an output `Path` into an assembled pipeline whose `run()` method renders each mirrored page to PDF bytes, assembles them, writes the combined output to disk, and returns a `PublicationArtifact`. Phase 7 does not implement a real PDF renderer — `PdfRenderer` and `PdfAssemblyStrategy` are interfaces that Phase 8 and Phase 10 will fill in. `SiteMirrorSource` connects the mirror output directory to the pipeline as a `PublicationSource`.

### Scope

**Included:**
- `PublicationSource` interface: `contentDir()` + `manifest()`
- `SiteMirrorSource`: `from(Path)` reads manifest from disk; `of(Path, MirrorManifest)` accepts in-memory manifest
- `PdfRenderer` interface: `byte[] render(Path htmlFile) throws IOException`
- `PdfAssemblyStrategy` interface: `byte[] assemble(List<byte[]> pages) throws IOException`
- `PublicationArtifact` record: path, format, sizeBytes, producedAt
- `PublicationPipeline` with fluent `Builder`; all four components required at build time
- `PublicationPipeline.run()` functional: calls renderer per page, assembler once, writes output file
- 14 tests covering builder validation, accessor round-trip, `SiteMirrorSource`, and end-to-end `run()` with fakes

**Explicitly excluded:**
- Real PDF renderer implementation (Phase 8)
- Real PDF assembly implementation (Phase 10)
- ePub support (Phase 11)
- Wiring into `SiteExporterCommand` CLI

### Deliverables

- `PublicationSource.java` — public interface
- `SiteMirrorSource.java` — public final class implementing `PublicationSource`
- `PdfRenderer.java` — public interface (Phase 8 will provide implementations)
- `PdfAssemblyStrategy.java` — public interface (Phase 10 will provide implementations)
- `PublicationArtifact.java` — public record
- `PublicationPipeline.java` — public final class + inner `Builder`
- `PublicationPipelineTest.java` — 14 tests

### Changed Files

| File | Change |
|---|---|
| `codex-ir-app/src/main/java/codex/apps/siteexporter/PublicationSource.java` | New |
| `codex-ir-app/src/main/java/codex/apps/siteexporter/SiteMirrorSource.java` | New |
| `codex-ir-app/src/main/java/codex/apps/siteexporter/PdfRenderer.java` | New |
| `codex-ir-app/src/main/java/codex/apps/siteexporter/PdfAssemblyStrategy.java` | New |
| `codex-ir-app/src/main/java/codex/apps/siteexporter/PublicationArtifact.java` | New |
| `codex-ir-app/src/main/java/codex/apps/siteexporter/PublicationPipeline.java` | New |
| `codex-ir-app/src/test/java/codex/apps/siteexporter/PublicationPipelineTest.java` | New |

### Validation

```
mvn test
Tests run: 125, Failures: 0, Errors: 0, Skipped: 0
```

All three modules pass on first run. 14 new tests added (was 111, now 125 total). No existing tests regressed.

### Tests

| Test | What it verifies |
|---|---|
| `buildShouldSucceedWithAllRequiredComponents` | Builder succeeds with all four components |
| `buildShouldThrowWhenSourceIsMissing` | NullPointerException when source omitted |
| `buildShouldThrowWhenRendererIsMissing` | NullPointerException when renderer omitted |
| `buildShouldThrowWhenAssemblyStrategyIsMissing` | NullPointerException when assembly strategy omitted |
| `buildShouldThrowWhenOutputIsMissing` | NullPointerException when output omitted |
| `accessorsShouldReturnProvidedComponents` | Accessors return exact objects set on builder |
| `siteMirrorSourceOfShouldExposeContentDirAndManifest` | `of()` factory preserves contentDir and manifest |
| `siteMirrorSourceFromShouldReadManifestFromDisk` | `from()` reads manifest from `mirror-manifest.json` |
| `runShouldCallRendererOncePerSuccessfulPage` | Renderer called exactly N times for N SUCCESS pages |
| `runShouldSkipWriteFailedPages` | WRITE_FAILED pages excluded from rendering |
| `runShouldCallAssemblyStrategyWithAllRenderedPages` | Assembler receives all per-page PDFs |
| `runShouldWriteAssembledBytesToOutputFile` | Output file created with exact assembled bytes |
| `runShouldReturnArtifactWithCorrectMetadata` | Artifact carries path, format=PDF, sizeBytes, producedAt |
| `runWithEmptyManifestShouldProduceEmptyArtifact` | Zero-page manifest produces empty artifact |

### Engineering Notes

- `PublicationPipeline.Builder` validates all four components at `build()` time (not `run()` time). This makes misconfigured pipelines fail immediately at construction rather than silently until execution.
- `PublicationPipeline.run()` is fully functional: it iterates SUCCESS pages, calls `renderer.render()` per page, and calls `assemblyStrategy.assemble()` once with all results. Phase 7 tests exercise the full call graph using fake implementations, confirming the wiring is correct.
- `SiteMirrorSource.of(contentDir, manifest)` is public and useful for in-memory manifests (post-mirror, in tests). `SiteMirrorSource.from(outputDir)` is for the CLI use case where the manifest is on disk.
- `PublicationArtifact` uses `PublicationFormat.PDF` hardcoded in `run()`. Phase 11 can generalize when ePub is added.
- The format carries in `PublicationArtifact` rather than in the pipeline itself. This leaves room for a pipeline that produces multiple formats in a single run.

### Decisions

- **All four builder parameters required at build time** — the plan says "Basic validation of required components." Making them all required removes the possibility of a pipeline that silently does nothing when `run()` is called.
- **`run()` is functional in Phase 7** — the plan says "Fake renderer/fake assembly tests." Making `run()` a real orchestrator rather than a stub allows the test to verify wiring end-to-end. A stub would only prove the builder API compiles.
- **`PdfRenderer` and `PdfAssemblyStrategy` have concrete method signatures** — marker interfaces would be type-safe but useless for testing. Defining `byte[] render(Path)` and `byte[] assemble(List<byte[]>)` gives the test fakes something to implement without committing to the full Phase 8/10 contract.
- **`PublicationPipelineBuilder` is an inner class of `PublicationPipeline`** — same pattern as `MirrorManifest.Builder`. The plan names them as separate deliverables but the inner class fulfills both.

### Tradeoffs

- `run()` loads all per-page PDF bytes into memory simultaneously before calling the assembler. For large mirrors this could exhaust heap. A streaming approach would be more memory-efficient but premature for Phase 7.
- `PublicationFormat.PDF` is hardcoded in `run()`. Adding ePub output requires a format parameter or strategy routing — deferred to Phase 11.

### Risks

- `PdfRenderer` and `PdfAssemblyStrategy` signatures may need to change in Phases 8 and 10 (e.g., adding `PdfRenderOptions` parameter). This would require updating `PublicationPipeline.run()` and all test fakes. Acceptable — the interfaces are internal to the package.

### Known Limitations

- No real PDF output. The pipeline is wired but produces whatever bytes the renderer/assembler return.
- `SiteExporterCommand` still does not invoke the pipeline. Full CLI wiring is deferred.

### Follow-ups

- Phase 8: implement `PdfRenderer` using OpenHTMLToPDF.
- Phase 10: implement `PdfAssemblyStrategy` using a PDF merge library (e.g. PDFBox or iText).
- Wire `SiteMirrorService` → `SiteAssetService` → `SiteLinkRewriteService` → `PublicationPipeline` into `SiteExporterCommand`.

### Next Step

**Phase 8 — PDF Renderer Port**: implement `PdfRenderer` using OpenHTMLToPDF behind the interface defined here. Add `PdfRenderOptions` record. Provide a smoke test rendering a real HTML file to a real PDF.

---

## Phase 6B — HTML Link Rewriting

### Summary

Rewrites all successfully-mirrored HTML pages in place so that the mirror is locally navigable. `HtmlLinkRewriter` uses Jsoup to parse each HTML document, looks up every `a[href]`, `img[src]`, `link[rel=stylesheet][href]`, and `script[src]` absolute URL against the two plan indexes (`PageLinkRewritePlan` from `MirrorManifest`, `AssetLinkRewritePlan` from `AssetManifest`), and replaces matched attributes with computed relative paths. `SiteLinkRewriteService` orchestrates reading each HTML file, applying the rewriter, and writing the result back to the same file. External links and references to unmirrored/undownloaded targets are left unchanged.

### Scope

**Included:**
- `a[href]` → internal page links rewritten to relative paths (`../about/index.html`)
- `img[src]`, `link[rel=stylesheet][href]`, `script[src]` → rewritten to local asset paths (`../../assets/img/logo.png`)
- External links left unchanged (host not in plan)
- Fragment links (`#anchor`) left unchanged (no HTTP/HTTPS scheme)
- Links to unmirrored pages or undownloaded assets left unchanged
- `prettyPrint(false)` to minimize whitespace changes to original HTML

**Explicitly excluded:**
- CSS `url(...)` rewriting
- `canonical`/`meta` URL rewriting
- PDF/ePub rendering
- Wiring into `SiteExporterCommand` CLI (deferred to when full pipeline is ready)

### Deliverables

- `PageLinkRewritePlan.java` — package-private; maps mirrored page URLs → `localHtmlPath`; built from `MirrorManifest`
- `AssetLinkRewritePlan.java` — package-private; maps downloaded asset URLs → `localAssetPath`; built from `AssetManifest`
- `HtmlLinkRewriter.java` — package-private; pure string-in/string-out rewriter; `computeRelativePath()` is package-private static for unit testing
- `SiteLinkRewriteService.java` — public; orchestrates in-place rewriting of all mirrored pages; returns count of pages rewritten
- `HtmlLinkRewriterTest.java` — 20 unit tests
- `SiteLinkRewriteServiceTest.java` — 10 integration tests

### Changed Files

| File | Change |
|---|---|
| `codex-ir-app/src/main/java/codex/apps/siteexporter/PageLinkRewritePlan.java` | New |
| `codex-ir-app/src/main/java/codex/apps/siteexporter/AssetLinkRewritePlan.java` | New |
| `codex-ir-app/src/main/java/codex/apps/siteexporter/HtmlLinkRewriter.java` | New |
| `codex-ir-app/src/main/java/codex/apps/siteexporter/SiteLinkRewriteService.java` | New |
| `codex-ir-app/src/test/java/codex/apps/siteexporter/HtmlLinkRewriterTest.java` | New |
| `codex-ir-app/src/test/java/codex/apps/siteexporter/SiteLinkRewriteServiceTest.java` | New |

### Validation

```
mvn test
Tests run: 111, Failures: 0, Errors: 0, Skipped: 0
```

All three modules pass on first run. 28 new tests added (was 83, now 111 total). No existing tests regressed.

Manual spot-check: on a 3-page mirror with cross-links, `index.html → about/index.html` becomes `about/index.html` (root-level relative) and `about/index.html → index.html` becomes `../index.html` (parent-relative). Asset path `section/page/index.html → assets/img/logo.png` becomes `../../assets/img/logo.png`.

### Tests

| Test class | Tests | Coverage |
|---|---|---|
| `HtmlLinkRewriterTest` | 20 | Internal link rewritten; external unchanged; unmirrored unchanged; fragment unchanged; nested page relative path; sibling relative path; multiple links; img src rewritten; stylesheet href; script src; undownloaded asset unchanged; nested page to asset path; `computeRelativePath` unit tests (5 cases); Jsoup round-trip content preservation |
| `SiteLinkRewriteServiceTest` | 10 | Internal link rewritten in-place; external unchanged; asset references (img+css+js); correct relative path from nested page; WRITE_FAILED pages skipped; return count correct; no-link page preserved; undownloaded asset unchanged; bidirectional cross-links correct |

### Engineering Notes

- `HtmlLinkRewriter.rewriteAttr()` uses `Function<URI, String>` (method reference to `pagePlan::localPath` / `assetPlan::localPath`) rather than a shared interface. Avoids creating a `LinkRewritePlan` interface for two callers.
- Jsoup's `element.absUrl(attr)` resolves relative URLs against the document base URI set during `Jsoup.parse(html, pageUrl.toString())`. This ensures that relative links in the original HTML are correctly resolved before lookup.
- `doc.outputSettings().prettyPrint(false)` prevents Jsoup from reformatting the HTML. Without this, Jsoup would add indentation and newlines, significantly changing the byte output of the file.
- `computeRelativePath()` handles the root-page case (`Path.of("index.html").getParent()` returns `null`) by substituting an empty path. `Path.of("").relativize(Path.of("about/index.html"))` correctly produces `"about/index.html"`.
- Only `SUCCESS` pages from `MirrorManifest` are indexed in `PageLinkRewritePlan`. This is intentional — rewriting links to `WRITE_FAILED` pages would create broken local links.
- `SiteLinkRewriteService.rewrite()` returns the count of rewritten pages (not void) to make tests more precise without adding a full report type.

### Decisions

- **In-place rewriting** — HTML files are overwritten rather than written to a separate `rewritten/` directory. This keeps the mirror structure flat and matches standard site-mirroring tools. A separate output directory would complicate the asset-path relativization since asset paths in the plans already assume `outputDir` as the root.
- **`SiteExporterCommand` not wired** — the CLI remains unchanged. Wiring the full pipeline (mirror → assets → rewrite) will be done when all three phases are stable enough to be exposed as a single command, likely before or during Phase 7.
- **`SiteLinkRewriteService` does not need an `AssetFetcher`-style injection overload** — the service only reads/writes the local filesystem and doesn't make network calls. `@TempDir` tests cover it fully.

### Tradeoffs

- Jsoup's `outerHtml()` may change attribute quoting style, self-closing tag format, or entity encoding. The HTML is semantically identical and will render correctly in browsers, but byte-for-byte identity with the original is not guaranteed. This is acceptable for a mirroring tool.
- The link lookup uses exact URI equality. URLs with differing query strings or trailing slashes that point to the same mirrored page will not be rewritten. This is correct behavior — the manifest only records the exact URL the crawler visited.

### Risks

- **Jsoup normalization of HTML5 attributes** — Jsoup may normalize boolean attributes or close void elements differently. Low risk for typical HTML pages.
- **In-place write overwrites original on partial failure** — if `SiteLinkRewriteService.rewrite()` throws mid-run, some pages will have been rewritten and some not. The service does not implement rollback. Acceptable for a local mirroring tool.

### Known Limitations

- CSS `url(...)` references are not rewritten. Background images and `@font-face` fonts referenced from stylesheets will still point to absolute URLs.
- `SiteExporterCommand` does not expose link rewriting via CLI. The service must be called programmatically.

### Follow-ups

- Wire `SiteMirrorService` → `SiteAssetService` → `SiteLinkRewriteService` into `SiteExporterCommand` as a single command.
- Consider a dry-run mode for `SiteLinkRewriteService` that counts planned rewrites without modifying files.

### Next Step

**Phase 7 — Publication Pipeline Builder**: compose `SiteMirrorService`, `SiteAssetService`, and `SiteLinkRewriteService` into a `PublicationPipeline` builder that wires the full mirror-to-PDF pipeline programmatically.

---

## Phase 6A — Asset Discovery and Download

### Summary

Added asset discovery and download on top of the mirror pipeline. After a site is mirrored to disk, `SiteAssetService` reads each successfully mirrored HTML file, extracts asset references (`img[src]`, `link[rel=stylesheet][href]`, `script[src]`) via Jsoup in document order, deduplicates across all pages by URL, applies a same-domain filter, downloads each unique asset using `java.net.http.HttpClient`, writes it under `outputDir/assets/`, and writes `asset-manifest.json`. Assets that fail to download or are skipped by the domain filter are recorded with `DOWNLOAD_FAILED` or `SKIPPED` status. All counts are derived from the asset list at manifest-build time.

### Scope

**Included:**
- `img[src]` → `IMAGE`, `link[rel=stylesheet][href]` → `STYLESHEET`, `script[src]` → `SCRIPT`
- Same-domain asset filter controlled by `SiteMirrorOptions.sameDomainOnly()`
- Deduplication by canonical asset URL across all mirrored pages
- Asset paths stored relative to `outputDir` (e.g. `assets/img/logo.png`)
- `SUCCESS`, `DOWNLOAD_FAILED`, `SKIPPED` status tracking
- `asset-manifest.json` written to the output directory
- Jackson round-trip for `AssetManifest` and `AssetMetadata`
- `AssetFetcher` functional interface for test injection (same pattern as `SiteMirrorService`)

**Explicitly excluded:**
- CSS `url(...)` asset discovery
- HTML link rewriting
- PDF/ePub rendering
- External asset download by policy (SKIPPED)

### Deliverables

- `AssetStatus.java` — enum: SUCCESS, DOWNLOAD_FAILED, SKIPPED
- `AssetType.java` — enum: IMAGE, STYLESHEET, SCRIPT
- `AssetReference.java` — package-private record (URI url, AssetType type)
- `AssetFetcher.java` — package-private @FunctionalInterface; nested `Result` record
- `AssetMetadata.java` — public record + Builder + Jackson binding
- `AssetManifest.java` — public record + Builder + Jackson binding; writeTo/readFrom delegates
- `AssetManifestWriter.java` — package-private; Jackson serializer with Instant SimpleModule
- `AssetManifestReader.java` — package-private; Jackson deserializer with Instant SimpleModule
- `AssetReferenceExtractor.java` — Jsoup-based; document-order traversal via `getAllElements()`
- `AssetLocalPathResolver.java` — wraps `LocalPathResolver` with `outputDir/assets/` base
- `SiteAssetService.java` — public; real HTTP method + package-private test-injection overload
- `AssetReferenceExtractorTest.java` — 12 tests
- `AssetLocalPathResolverTest.java` — 8 tests
- `SiteAssetServiceTest.java` — 12 tests
- `codex-ir-app/pom.xml` — added `jsoup` dependency
- `module-info.java` — added `requires org.jsoup`, `requires java.net.http`

### Changed Files

| File | Change |
|---|---|
| `codex-ir-app/pom.xml` | Added `jsoup` dependency |
| `codex-ir-app/src/main/java/module-info.java` | Added `requires org.jsoup`, `requires java.net.http` |
| `codex-ir-app/src/main/java/codex/apps/siteexporter/AssetStatus.java` | New |
| `codex-ir-app/src/main/java/codex/apps/siteexporter/AssetType.java` | New |
| `codex-ir-app/src/main/java/codex/apps/siteexporter/AssetReference.java` | New |
| `codex-ir-app/src/main/java/codex/apps/siteexporter/AssetFetcher.java` | New |
| `codex-ir-app/src/main/java/codex/apps/siteexporter/AssetMetadata.java` | New |
| `codex-ir-app/src/main/java/codex/apps/siteexporter/AssetManifest.java` | New |
| `codex-ir-app/src/main/java/codex/apps/siteexporter/AssetManifestWriter.java` | New |
| `codex-ir-app/src/main/java/codex/apps/siteexporter/AssetManifestReader.java` | New |
| `codex-ir-app/src/main/java/codex/apps/siteexporter/AssetReferenceExtractor.java` | New |
| `codex-ir-app/src/main/java/codex/apps/siteexporter/AssetLocalPathResolver.java` | New |
| `codex-ir-app/src/main/java/codex/apps/siteexporter/SiteAssetService.java` | New |
| `codex-ir-app/src/test/java/codex/apps/siteexporter/AssetReferenceExtractorTest.java` | New |
| `codex-ir-app/src/test/java/codex/apps/siteexporter/AssetLocalPathResolverTest.java` | New |
| `codex-ir-app/src/test/java/codex/apps/siteexporter/SiteAssetServiceTest.java` | New |

### Validation

```
mvn test
Tests run: 83, Failures: 0, Errors: 0, Skipped: 0
```

All three modules pass. New tests: 12 + 8 + 12 = 32 tests added (was 51, now 83 total across all app tests). Two initial test failures were corrected before final commit:

1. `shouldPreserveInsertionOrderAcrossTypes` — Extractor initially processed by selector type (all img first, then link, then script). Changed to `doc.getAllElements()` single-pass to produce document order. Test expectation was correct.
2. `shouldNeverEscapeOutputDir` — Expected `SecurityException` but `..` segments are sanitized away before path resolution (same behavior as `LocalPathResolver`). Updated test to assert that the path stays safely within `outputDir/assets/` rather than expecting an exception.

### Tests

| Test class | Tests | Coverage |
|---|---|---|
| `AssetReferenceExtractorTest` | 12 | img/link/script extraction; relative URL resolution; absolute URLs unchanged; deduplication within page; non-http filtered; img without src; link without stylesheet rel; empty HTML; document order preserved |
| `AssetLocalPathResolverTest` | 8 | image/CSS/script resolved under `assets/`; relativized to outputDir; assets/ prefix; `..` safely stripped; no path separator regression |
| `SiteAssetServiceTest` | 12 | download success; HTTP error → DOWNLOAD_FAILED; network error → DOWNLOAD_FAILED; same-domain skip; same-domain=false downloads external; deduplication across pages; multi-type single page; manifest JSON written; manifest round-trip; empty page list; WRITE_FAILED pages skipped; relative localAssetPath |

### Engineering Notes

- `AssetReferenceExtractor` uses `doc.getAllElements()` rather than separate `select()` calls to process assets in document order. Jsoup guarantees `getAllElements()` follows depth-first document order, so `link[rel=stylesheet]` in `<head>` appears before `img[src]` in `<body>`.
- `SiteAssetService` collects all references first (across all pages), deduplicates into a `LinkedHashMap`, then downloads. This ensures same-asset references from multiple pages are downloaded exactly once.
- `AssetLocalPathResolver` delegates all sanitization and traversal safety to `LocalPathResolver` with `outputDir/assets/` as the base. `relativize()` produces paths relative to `outputDir` (not to `assets/`), so stored paths have the `assets/` prefix.
- Counts (`totalCount`, `successfulCount`, `failedCount`, `skippedCount`) are computed from the assets list in `Builder.build()`, same pattern as `MirrorManifest`. JSON-stored count fields are ignored on deserialization via `@JsonIgnoreProperties(ignoreUnknown = true)`.
- `AssetManifestWriter` and `AssetManifestReader` replicate the `ObjectMapper` setup from `ManifestWriter`/`ManifestReader`. The duplication is intentional — three similar lines rather than a premature shared-mapper abstraction.

### Decisions

- **`SiteAssetService` not wired into `SiteExporterCommand`** — the command still only invokes `SiteMirrorService`. Wiring `SiteAssetService` into the CLI entry point is deferred to a follow-up or when Phase 6B link rewriting is complete, so both phases can be triggered together.
- **`SiteMirrorOptions` reused for asset service** — rather than creating a separate `SiteAssetOptions`, the existing options provide all necessary configuration (`outputDir`, `seedUrl`, `sameDomainOnly`). Avoids a new type for no gain.
- **`script[src]` included** — the plan listed it as optional. Since it's symmetric with `img[src]` and the infrastructure is identical, it was included. For PDF rendering CSS and images matter more than JS, but JS doesn't harm the manifest.

### Tradeoffs

- `AssetManifestWriter`/`AssetManifestReader` duplicate the `createMapper()` logic. Extracting a shared `SiteExporterJackson` factory class is cleaner but premature — there are only two callers and both are in the same package.
- `SiteAssetService` reads HTML files from disk synchronously. For large mirrors this will be slow. A streaming approach (process as the mirror runs) would be more efficient but complicates the service boundary. Deferred.
- `AssetLocalPathResolver` maps the asset URL path into `outputDir/assets/<url-path>`. Two different asset URLs from different domains that share the same path would collide (e.g. `https://a.com/img.png` and `https://b.com/img.png` both resolve to `assets/img.png`). Accepted since same-domain filtering is the default.

### Risks

- **Disk write for assets uses `Files.write(path, bytes)` which overwrites silently** — if the same path is resolved twice (content-address collision, see tradeoff above), the second write silently overwrites the first. Low risk in practice due to same-domain filtering.
- **No redirect loop protection in `httpFetcher()`** — `HttpClient.Redirect.NORMAL` follows redirects but doesn't cap the chain length. Unlikely to matter for static assets but not guarded.

### Known Limitations

- `SiteAssetService` is not yet wired into `SiteExporterCommand`. Running the CLI only mirrors HTML; assets must be downloaded via a separate programmatic call.
- CSS `url(...)` references (background images, `@font-face`, etc.) are not discovered or downloaded.
- No timeout on individual asset HTTP requests.

### Follow-ups

- Wire `SiteAssetService` into `SiteExporterCommand` after Phase 6B link rewriting is done.
- Add CSS `url(...)` asset discovery in a follow-up pass.
- Add per-request timeout to `httpFetcher()`.

### Next Step

**Phase 6B — HTML Link Rewriting**: rewrite `a[href]` to local relative paths and asset references (`img[src]`, `link[href]`, `script[src]`) to the downloaded local asset paths, making the mirror locally navigable.

---

## Phase 5 Fix — Jackson Binding Manifest Reader

### Summary

`ManifestReader` was replaced from manual `JsonNode` field-by-field extraction to a single `MAPPER.readValue(source.toFile(), MirrorManifest.class)` call. Both `MirroredPage` and `MirrorManifest` were annotated with `@JsonDeserialize(builder = ...)` and their inner `Builder` classes with `@JsonPOJOBuilder(withPrefix = "")`, making Jackson drive deserialization through the existing Builders. `ManifestWriter` was similarly collapsed to `MAPPER.writerWithDefaultPrettyPrinter().writeValue(...)`. A custom `SimpleModule` handles `Instant` serialization and deserialization (ISO-8601 strings) without requiring `jackson-datatype-jsr310`. `MirroredPage.depth` was changed from primitive `int` to nullable `Integer` and `discoveredOrder` from `int` to `long`. `SiteMirrorService` now passes `null` for depth since the crawler does not expose it; `0` is now semantically reserved for the seed/root page.

### Scope

**Included:**
- `ManifestReader` replaced with `MAPPER.readValue()` — no more `JsonNode` traversal
- `ManifestWriter` replaced with `MAPPER.writeValue()` — no more `ObjectNode` building
- `@JsonDeserialize(builder = ...)` + `@JsonPOJOBuilder(withPrefix = "")` on `MirroredPage` and `MirrorManifest`
- `@JsonIgnoreProperties(ignoreUnknown = true)` on both Builders (derived count fields in `MirrorManifest` JSON are ignored on read; counts are always recomputed from pages)
- `opens codex.apps.siteexporter to com.fasterxml.jackson.databind;` in `module-info.java` so Jackson can access private Builder constructors
- `MirroredPage.depth` → `Integer` (null = unknown, 0 = seed)
- `MirroredPage.discoveredOrder` → `long`
- `SiteMirrorService` passes `null` for depth
- 2 new round-trip tests: null depth, depth=0 distinct from null

**Excluded:** no functional changes to crawling, manifest schema, or output format.

### Deliverables

- `ManifestReader.java` — 50 lines → 45 lines; one `readValue` call
- `ManifestWriter.java` — 82 lines → 45 lines; one `writeValue` call
- `MirroredPage.java` — `@JsonDeserialize` + `@JsonPOJOBuilder` + `@JsonIgnoreProperties`; `depth: Integer`, `discoveredOrder: long`
- `MirrorManifest.java` — `@JsonDeserialize` + `@JsonPOJOBuilder` + `@JsonIgnoreProperties` on Builder
- `module-info.java` — `opens` directive
- `SiteMirrorService.java` — `depth(null)`, `discoveredOrder` as `long`
- `ManifestWriterTest.java` — 2 new tests
- `SiteMirrorServiceTest.java` — depth null assertion added

### Changed Files

| File | Change |
|---|---|
| `codex-ir-app/src/main/java/module-info.java` | Added `opens codex.apps.siteexporter to com.fasterxml.jackson.databind` |
| `codex-ir-app/src/main/java/codex/apps/siteexporter/MirroredPage.java` | `@JsonDeserialize` + `@JsonPOJOBuilder` + `@JsonIgnoreProperties`; depth→Integer; discoveredOrder→long |
| `codex-ir-app/src/main/java/codex/apps/siteexporter/MirrorManifest.java` | `@JsonDeserialize` + `@JsonPOJOBuilder` + `@JsonIgnoreProperties` on Builder |
| `codex-ir-app/src/main/java/codex/apps/siteexporter/ManifestWriter.java` | Collapsed to `MAPPER.writeValue()`; SimpleModule for Instant |
| `codex-ir-app/src/main/java/codex/apps/siteexporter/ManifestReader.java` | Collapsed to `MAPPER.readValue()`; SimpleModule for Instant |
| `codex-ir-app/src/main/java/codex/apps/siteexporter/SiteMirrorService.java` | depth(null); discoveredOrder as long |
| `codex-ir-app/src/test/java/codex/apps/siteexporter/ManifestWriterTest.java` | +2 depth round-trip tests |
| `codex-ir-app/src/test/java/codex/apps/siteexporter/SiteMirrorServiceTest.java` | assertNull(entry.depth()) |

### Validation

```
mvn test
Tests run: 50, Failures: 0, Errors: 0, Skipped: 0
```

All modules pass. `ManifestWriterTest` now has 8 tests (was 6); `SiteMirrorServiceTest` has 9 tests (unchanged count, one assertion added).

### Tests

| Test | What it verifies |
|---|---|
| `nullDepthShouldRoundTripAsNull` | A page with `depth=null` serializes to JSON with `null` depth and deserializes back to `null` |
| `depthZeroIsDistinctFromNullDepth` | `depth=0` (root page) and `depth=null` (unknown) survive round-trip as distinct values |

### Engineering Notes

- `@JsonIgnoreProperties(ignoreUnknown = true)` on `MirrorManifest.Builder` ensures the derived JSON fields (`documentCount`, `successfulCount`, `failedCount`, `skippedCount`) don't cause Jackson to fail when deserializing — they have no matching Builder setters and are intentionally ignored.
- The `opens codex.apps.siteexporter to com.fasterxml.jackson.databind;` directive is the standard JPMS + Jackson pattern. Without it, Jackson cannot call `setAccessible(true)` on the private `Builder()` constructors and throws `InaccessibleObjectException`.
- `ManifestReader` and `ManifestWriter` each own their own `ObjectMapper` instance. They have slightly different configurations (writer needs Instant serializer; reader needs Instant deserializer + `FAIL_ON_UNKNOWN_PROPERTIES=false`). Sharing a mapper was deferred as premature abstraction.
- `Instant` is handled via a `SimpleModule` rather than `jackson-datatype-jsr310` to avoid adding a new pom dependency.

### Decisions

- **`@JsonDeserialize(builder = ...)` over `@JsonCreator` on canonical constructor** — The Builder approach preserves the count-derivation logic in `MirrorManifest.Builder.build()`. Using `@JsonCreator` on the canonical constructor would pass the JSON-stored counts directly into the record, breaking the "counts derived from pages" invariant.
- **`Integer` not `int` for `depth`** — Primitive `int` cannot be null. A sentinel value like `0` or `-1` for "unknown" is semantically ambiguous since `0` is a valid depth (seed page).
- **`long` for `discoveredOrder`** — Per the task brief; `int` is sufficient for practical page counts but `long` is the safer type for a monotonically-increasing counter.

### Tradeoffs

- The `opens` directive widens Jackson's reflection access to the entire `codex.apps.siteexporter` package (not just the manifest types). This is standard practice but is broader than strictly necessary.
- Two `ObjectMapper` instances with similar but distinct configurations. The duplication is minimal and avoids a premature shared-mapper abstraction.

### Risks

- If any new type is added to `codex.apps.siteexporter` that should NOT be accessible to Jackson, the `opens` directive would still expose it. Low risk for an application module.

### Known Limitations

- `depth` and `parentUrl` are still always null at runtime (crawler doesn't expose them). The type change to `Integer` makes the nullability semantically correct but doesn't populate the field.

### Follow-ups

- Expose crawl depth from the traversal crawler to populate `MirroredPage.depth` correctly.
- Consider a shared `ManifestMapper` factory if the writer/reader configurations converge or a third caller appears.

### Next Step

**Phase 6 — Asset Download and Link Rewriting**: the manifest contract is now stable and portable. Phase 6 can consume it to locate mirrored HTML files and produce an asset inventory.

---

## Phase 5 — Robust Manifest Metadata + Manifest Hygiene

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

## Phase 4 — Mirror HTML Vertical Slice

### What was built

Five new types in `codex.apps.siteexporter`:

| Type | Kind | Purpose |
|---|---|---|
| `LocalPathResolver` | final class | Maps a URI to a local `Path` inside the output directory |
| `HtmlPageWriter` | final class | Writes `WebPage.rawHtml()` to disk via `LocalPathResolver` |
| `MirroredPage` | record | One manifest entry: URL, local path, title, status, fetchedAt |
| `MirrorManifest` | record | Holds all `MirroredPage` entries; serializes to `mirror-manifest.json` |
| `SiteMirrorService` | final class | Orchestrates crawl → write → manifest; package-private overload accepts injected `DocumentSource<WebPage>` for testing |

`SiteExporterCommand` was refactored: `parseArgs` now throws `IllegalArgumentException` instead of calling `System.exit`, making it unit-testable. `--no-same-domain` flag wired. `SiteMirrorService` wired into `main()`.

### Files changed

- `codex-ir-app/src/main/java/codex/apps/siteexporter/LocalPathResolver.java` — new
- `codex-ir-app/src/main/java/codex/apps/siteexporter/HtmlPageWriter.java` — new
- `codex-ir-app/src/main/java/codex/apps/siteexporter/MirroredPage.java` — new
- `codex-ir-app/src/main/java/codex/apps/siteexporter/MirrorManifest.java` — new
- `codex-ir-app/src/main/java/codex/apps/siteexporter/SiteMirrorService.java` — new
- `codex-ir-app/src/main/java/codex/apps/siteexporter/SiteExporterCommand.java` — refactored
- `codex-ir-app/src/test/java/codex/apps/siteexporter/LocalPathResolverTest.java` — new
- `codex-ir-app/src/test/java/codex/apps/siteexporter/HtmlPageWriterTest.java` — new
- `codex-ir-app/src/test/java/codex/apps/siteexporter/SiteMirrorServiceTest.java` — new
- `codex-ir-app/src/test/java/codex/apps/siteexporter/SiteExporterCommandTest.java` — new

### Validation

- `mvn test -pl codex-ir-app`: **32 tests, 0 failures, 0 errors**
  - `SitemapUrlExtractorTest`: 8/8 (pre-existing, no regression)
  - `LocalPathResolverTest`: 8/8
  - `HtmlPageWriterTest`: 4/4
  - `SiteMirrorServiceTest`: 6/6
  - `SiteExporterCommandTest`: 6/6
- URI → path mapping verified across root, trailing-slash, no-extension, and extension cases.
- Output files verified via `@TempDir` isolation.
- Manifest JSON verified for required fields and correct page count.
- `outputDir`-is-a-file guard verified.
- `sameDomainOnly=true` confirmed as default in both options builder and command parser.

### Tradeoffs

- **Manifest JSON hand-crafted** — Jackson is available in `codex-ir-web` but not re-exported to `codex-ir-app`. Adding a `requires com.fasterxml.jackson.databind` to `module-info.java` would work without a pom.xml change, but hand-crafting keeps the dependency surface minimal for Phase 4. Phase 5 can revisit.
- **`SiteMirrorService.mirror(options)` closes `WebCrawlerRuntime.getInstance()`** — this singleton is safe to close here because the mirror command is the only runtime user. If future commands share the runtime, this needs revisiting.
- **`LocalPathResolver` does not sanitize path segments** — on a controlled dev tool this is acceptable. A web-facing mirror tool would need to strip traversal characters (e.g. `../`).

### Risks and limitations

- No integration test against a live site (intentional — validation plan requires a tiny local/static test site; that is deferred to the evaluator or a future fixture-based test).
- `MirrorManifest` stores absolute `localPath` values — these are not portable across machines. Phase 5 should switch to paths relative to `outputDir`.
- Failed page writes log to stderr and are silently skipped; they do not appear in the manifest. This is acceptable for Phase 4 but should be surfaced in the manifest in Phase 5.

### Next steps

- **Phase 5** — expand `MirroredPage` to the full metadata schema (`id`, `canonicalUrl`, `depth`, `discoveredOrder`, `parentUrl`, `contentType`, `status`, `fetchedAt`); switch manifest paths to relative; add manifest round-trip tests.

---

## Phase 3 — Site Exporter Skeleton

### What was built

Introduced the `codex.apps.siteexporter` package inside `codex-ir-app` with four types:

| Type | Kind | Purpose |
|---|---|---|
| `PublicationFormat` | enum | Supported output formats: `PDF`, `EPUB` |
| `SiteMirrorOptions` | record + Builder | Crawl configuration: seed URL, output directory, page/depth limits, domain scope |
| `PublicationExportOptions` | record + Builder | Export configuration: target format and output path |
| `SiteExporterCommand` | final class | CLI entry point; parses args into option types; pipeline wiring is a stub |

### Validation

- `mvn compile` — passes, no errors
- `mvn test` — all existing tests pass; no new tests added (Phase 3 is structural only)

### Tradeoffs

- `SiteExporterCommand.main()` calls `System.exit(1)` on missing `--url`. This is standard for CLI entry points but makes unit-testing the parser awkward if needed later. A `parseArgs` method that returns `Optional<ParsedArgs>` or throws a typed exception would be cleaner — deferred to Phase 4 when real integration is wired.
- `sameDomainOnly` is hardcoded to `true` in `SiteExporterCommand`. Exposed on `SiteMirrorOptions.Builder` for future flag wiring.

### Risks and limitations

- Pipeline is entirely unimplemented. `SiteExporterCommand.main()` prints a stub message and exits.
- No validation that `--out-dir` exists or is writable — intentionally deferred to Phase 4 (mirror) where the directory is actually needed.

### Next steps

- **Phase 4** — implement `SiteMirrorService` using the traversal crawler to write HTML pages to `outputDir` and produce a `mirror-manifest.json`.
