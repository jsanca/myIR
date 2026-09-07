---
type: Engineering Log Entry
title: "Phase 10.9 — Markdown Publication Writer"
description: "Canonical engineering evidence for Phase 10.9 — Markdown Publication Writer."
tags: [site-exporter, engineering-log, migration]
timestamp: 2026-07-05T00:00:00Z
ckf_version: "0.1"
ckf_status: completed
ckf_scope: site-exporter
ckf_owner: project
---
# Phase 10.9 — Markdown Publication Writer

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
