---
type: Engineering Log Entry
title: "Phase 11 — EPUB Publication Driver"
description: "Canonical engineering evidence for Phase 11 — EPUB Publication Driver."
tags: [site-exporter, engineering-log, migration]
timestamp: 2026-07-05T00:00:00Z
ckf_version: "0.1"
ckf_status: completed
ckf_scope: site-exporter
ckf_owner: project
---
# Phase 11 — EPUB Publication Driver

### Summary

Added EPUB 3 as a third publication output format using the existing `PublicationDriver` architecture. `EpubPublicationDriver` produces a valid EPUB ZIP from a site mirror using Java's standard `java.util.zip` — no third-party EPUB library required. Text is extracted via the new `ReadablePageExtractor` shared helper, which also refactors `MarkdownPublicationWriter` to remove its inline detection/extraction code. `PublicationDrivers.forFormat(EPUB, dir)` now returns the driver instead of throwing. `SiteExporterCommand` required no publication branching changes — the driver pattern absorbs the new format transparently.

### Dependency

No new Maven dependency. EPUB is assembled using `java.util.zip.ZipOutputStream` from `java.base`. `com.positiondev.epublib:epublib-core:3.1` was evaluated and rejected: it is not on Maven Central and has JPMS compatibility issues with Java 25.

### Scope

**Included:**
- `ReadablePageExtractor` — shared pdf2htmlEX + normal-HTML text extractor returning `ReaderDocument`
- `EpubPublicationDriver` — EPUB 3 ZIP assembly, one chapter per mirrored page
- `PublicationDrivers` — EPUB case wired (replaces `IllegalArgumentException` throw)
- `MarkdownPublicationWriter` — refactored to use `ReadablePageExtractor` (extraction code removed)
- `SiteExporterCommand` — EPUB default output path (`./output.epub`), updated format hint
- 37 new tests across `EpubPublicationDriverTest`, `ReadablePageExtractorTest`, and updated `PublicationDriversTest` / `SiteExporterCommandTest`

**Excluded:**
- Images in EPUB chapters
- Custom CSS beyond minimal inline styles
- Heading hierarchy preservation
- EPUB metadata beyond title, identifier, language, modified date
- EPUB 2 NCX (EPUB 3 nav.xhtml is sufficient)

### Deliverables

- `ReadablePageExtractor.java` — new shared extractor
- `EpubPublicationDriver.java` — new EPUB driver (~220 source lines)
- `ReadablePageExtractorTest.java` — 12 tests
- `EpubPublicationDriverTest.java` — 22 tests
- `MarkdownPublicationWriter.java` — refactored (Jsoup imports and inline extraction removed)
- `PublicationDrivers.java` — EPUB case wired
- `SiteExporterCommand.java` — EPUB default path, updated format warning

### Changed Files

| File | Change |
|---|---|
| `codex-ir-app/src/main/java/codex/apps/siteexporter/ReadablePageExtractor.java` | Created |
| `codex-ir-app/src/main/java/codex/apps/siteexporter/EpubPublicationDriver.java` | Created |
| `codex-ir-app/src/main/java/codex/apps/siteexporter/MarkdownPublicationWriter.java` | Refactored to use `ReadablePageExtractor` |
| `codex-ir-app/src/main/java/codex/apps/siteexporter/PublicationDrivers.java` | EPUB case wired |
| `codex-ir-app/src/main/java/codex/apps/siteexporter/SiteExporterCommand.java` | EPUB default path, format hint |
| `codex-ir-app/src/test/java/codex/apps/siteexporter/ReadablePageExtractorTest.java` | Created |
| `codex-ir-app/src/test/java/codex/apps/siteexporter/EpubPublicationDriverTest.java` | Created |
| `codex-ir-app/src/test/java/codex/apps/siteexporter/PublicationDriversTest.java` | EPUB type test, exhaustiveness test |
| `codex-ir-app/src/test/java/codex/apps/siteexporter/SiteExporterCommandTest.java` | EPUB default path test |

### Validation

```
mvn test -pl codex-ir-app
Tests run: 359, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

- `epubShouldContainMimetypeEntry` + `epubMimetypeShouldBeApplicationEpubZip` — mimetype entry is first and correct ✓
- `epubShouldContainContainerXml` / `ContentOpf` / `NavXhtml` / `ChapterFile` — all required ZIP entries present ✓
- `contentOpfShouldListChaptersInManifestAndSpine` — manifest and spine reference correct chapter IDs ✓
- `epubChapterShouldContainPdf2HtmlExContent` — "Part I", subtitle, body text extracted from fixture ✓
- `epubChapterShouldNotContainCssOrScriptsFromPdf2HtmlExPage` — no `@font-face`, `position:absolute` ✓
- `epubChapterOrderShouldFollowDiscoveredOrder` — chapter-001/002 content matches page order ✓
- `forFormatShouldHandleAllKnownFormats` — iterates `PublicationFormat.values()`, confirms no throws ✓
- All 321 prior tests continue to pass ✓

### Tests

| Class | Tests | Status |
|---|---|---|
| `EpubPublicationDriverTest` | 22 | Added |
| `ReadablePageExtractorTest` | 12 | Added |
| `PublicationDriversTest` | +3 (EPUB type, EPUB asset, exhaustiveness) | Updated |
| `SiteExporterCommandTest` | +1 (EPUB default path) | Updated |

### Engineering Notes

- **No external EPUB library**: EPUB 3 is a well-specified ZIP format. The four structural files (`mimetype`, `container.xml`, `content.opf`, `nav.xhtml`) plus per-chapter XHTML are straightforward to generate with string building. The `mimetype` entry must be STORED (no compression) and first in the ZIP — this is achieved by writing it before `setMethod(DEFLATED)`.
- **`ReadablePageExtractor` as shared helper**: both `MarkdownPublicationWriter` and `EpubPublicationDriver` now share one extraction path. `MarkdownPublicationWriter` dropped its `Pdf2HtmlExDetector`, `Pdf2HtmlExTextExtractor`, and `extractNormalHtmlMarkdown` fields/methods — ~30 lines removed.
- **Chapter title resolution**: prefers the HTML `<title>` element (if not the default "Document" fallback), then the last URL path segment, then `Chapter N`. This produces readable ToC entries for most mirrored sites.
- **UUID per publish call**: `content.opf` uses `UUID.randomUUID()` for the unique identifier. Each publish run produces a distinct identifier, which is correct EPUB behavior.
- **`SiteExporterCommand` unchanged for publication logic**: `driver.requiresAssetProcessing()` returns `false` for EPUB (same as Markdown), so the asset/link stages are skipped automatically.

### Decisions

- **`java.util.zip` over epublib**: avoids a dependency that is not on Maven Central, has unknown JPMS compatibility, and is larger than the problem requires. The EPUB spec for a simple reader-mode publication is about 4 XML files plus chapters.
- **`ReadablePageExtractor` as a top-level class (not inner)**: both `MarkdownPublicationWriter` and `EpubPublicationDriver` need it. A top-level class is independently testable and avoids coupling those two drivers.
- **`EpubChapter` as a private record inside `EpubPublicationDriver`**: it is an implementation detail of the ZIP assembly; there is no reason to expose it.

### Tradeoffs

- **String-based XML generation over DOM/JAXB**: avoids adding XML serialization machinery. The EPUB XML is simple and predictable; manual string building is readable and produces correct output. Risk: a future change to escaping or namespaces requires careful string editing. Mitigated by `escapeXml()` helper and tests that verify specific XML content.
- **One `PublicationOrderingStrategy` hardcoded in `EpubPublicationDriver`**: uses `DiscoveredOrderPublicationOrderingStrategy` directly, consistent with `PdfPublicationDriver`. An ordering-strategy parameter could be exposed via a builder, but the other drivers don't do this.

### Risks

- EPUB validity: the output is structurally correct per EPUB 3 spec but has not been validated with `epubcheck`. Readers that perform strict validation may flag missing metadata (e.g. `dc:creator`). This is acceptable for reader-mode export.
- XML special characters: `escapeXml()` escapes `&`, `<`, `>`, `"`. If chapter content contains `'`, it will appear unescaped (valid in XML element content). This is intentional.

### Known Limitations

- No images — only extracted text appears in chapters.
- No `dc:creator` metadata in `content.opf`.
- Has not been validated with `epubcheck`.
- Heading hierarchy from normal HTML pages is not preserved (headings are extracted as plain paragraphs by `ReadablePageExtractor`).

### Follow-ups

1. Run `epubcheck` against a generated EPUB to verify spec compliance.
2. Add `dc:creator` metadata option to `EpubPublicationDriver` (could read from manifest startUrl or a CLI flag).
3. Add `--epub-pages-dir` for per-chapter XHTML side output (analogous to `markdown-pages/`).
4. Validate against a real Deep Learning Book pdf2htmlEX export.

### Next Step

Validate end-to-end: run `--from-mirror <dir> --format epub --output book.epub` on a real mirror and open the result in an EPUB reader (e.g. Calibre or Apple Books) to verify chapter rendering and table of contents.
