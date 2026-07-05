# Site Exporter Implementation Plan

## Objective

Build a `site-exporter` app on top of myIR that can:

1. Mirror a website to local disk using the existing traversal crawler.
2. Store enough metadata to reproduce, inspect, and transform the mirrored site.
3. Export the mirrored site to PDF.
4. Prepare the architecture for future publication formats such as ePub.

## Guiding Principles

* Keep `codex-ir-core` agnostic.
* Reuse `codex-ir-web` traversal crawler and web primitives.
* Keep app-specific logic inside `codex-ir-app`.
* Treat PDF/ePub as publication adapters behind ports.
* Prefer small vertical slices.
* Every task must update `docs/apps/site-exporter/ENGINEERING_LOG.md`.
* Every task must report validation, tests, tradeoffs, risks, limitations, and next steps.

---

## Phase 0 — Existing Scraper Organization

Status: completed by moving scraper code under `scraper`.

Goal:

```text
Keep existing scraping app code organized and away from core IR.
```

Evaluator should verify:

```text
- Behavior unchanged.
- Core remains clean.
- Existing tests still pass.
```

---

## Phase 1 — Work Contract Hardening

Goal:

```text
Update CLAUDE.md and AGENTS.md with the stronger reporting contract.
```

Deliverables:

```text
- Strong Reporting section.
- Architecture Discipline section.
- ENGINEERING_LOG.md requirement.
```

Validation:

```text
- Manual review of CLAUDE.md and AGENTS.md.
```

---

## Phase 2 — Test Plan and Evaluator Checklist

Goal:

```text
Define tests before implementation.
```

Deliverables:

```text
- docs/apps/site-exporter/TEST_PLAN.md
- Evaluator checklist for Elito
```

Validation:

```text
- Manual review.
- Confirm test plan covers mirror, manifest, renderer port, assembly, builder, and boundaries.
```

---

## Phase 3 — Site Exporter Skeleton

Goal:

```text
Create the app boundary without implementing the full pipeline.
```

Deliverables:

```text
- apps/siteexporter package
- SiteExporterCommand or equivalent app entrypoint
- SiteMirrorOptions
- PublicationExportOptions
- PublicationFormat
- Empty or minimal ENGINEERING_LOG.md entry
```

Validation:

```text
- mvn test
- App compiles
```

---

## Phase 4 — Mirror HTML Vertical Slice

Goal:

```text
Use the traversal crawler to download HTML pages to disk.
```

Deliverables:

```text
- SiteMirrorService
- SiteMirrorPipeline or mirror sink
- HtmlPageWriter
- LocalPathResolver
- Basic mirror-manifest.json
```

Behavior:

```text
start URL → traversal crawler → local HTML files → manifest
```

Validation:

```text
- Mirror a tiny local/static test site.
- Verify output files exist.
- Verify maxPages and sameHostOnly.
```

---

Sí. Yo modificaría **Phase 5** para que no sea solo “agregar campos”, sino también **cerrar la deuda técnica nacida en Phase 4** que afecta directamente al manifest.

La deuda detectada fue clara: JSON hecho a mano, `localPath` absoluto, fallos omitidos del manifest y `LocalPathResolver` sin sanitización. Clio mismo reportó esos puntos como tradeoffs/limitaciones de Phase 4.

Yo dejaría Phase 5 así:

````markdown id="negbsk"
## Phase 5 — Robust Manifest Metadata + Manifest Hygiene

### Goal

Make the mirror manifest portable, maintainable, safe, and useful for later publication assembly.

The manifest should stop being a Phase 4 prototype and become the stable contract between:

```text
site mirror → publication pipeline → PDF/ePub assembly
````

### Required document fields

```text
id
url
canonicalUrl
localHtmlPath
title
depth
discoveredOrder
parentUrl
contentType
status
fetchedAt
mirrorStatus
errorMessage
```

### Required manifest-level fields

```text
manifestVersion
startUrl
generatedAt
outputDir
sameDomainOnly
maxPages
maxDepth
documentCount
successfulCount
failedCount
skippedCount
```

### Deliverables

```text
- Replace hand-crafted JSON with Jackson.
- Add ManifestWriter.
- Add ManifestReader.
- Expand MirroredPage or replace it with DocumentMetadata.
- Store localHtmlPath relative to outputDir, not absolute.
- Add mirrorStatus for SUCCESS, FETCH_FAILED, WRITE_FAILED, SKIPPED.
- Include failed/skipped pages in the manifest or mirror report.
- Sanitize LocalPathResolver path segments.
- Add path traversal safety tests.
- Add manifest serialization/deserialization round-trip tests.
- Update SiteMirrorService to produce the robust manifest.
```

### Validation

```text
- mvn test -pl codex-ir-app
- Unit tests for manifest serialization.
- Unit tests for manifest deserialization.
- Round-trip test: object → JSON → object.
- Manifest entries for successful pages point to existing local files.
- Manifest paths are relative, portable paths.
- Failed/skipped entries are represented clearly.
- LocalPathResolver never writes outside outputDir.
```

### Explicitly out of scope

```text
- Asset manifest.
- Link rewriting.
- PDF rendering.
- PDF assembly.
- ePub.
```

### Technical debt intentionally carried forward

```text
- WebCrawlerRuntime lifecycle ownership remains a follow-up unless it is trivial to improve safely.
```
----

````markdown id="bswp80"
# Phase 5 — Robust Manifest Metadata + Manifest Hygiene

We completed Phase 4: the site exporter can mirror HTML pages and produce a basic `mirror-manifest.json`.

Now we need to turn the manifest into a stable, maintainable contract for later publication assembly.

## Goal

Make the mirror manifest portable, maintainable, safe, and useful for later PDF/ePub publication assembly.

## Context from Phase 4

Phase 4 intentionally left these debts:

- Manifest JSON is hand-crafted.
- `MirrorManifest` stores absolute `localPath` values.
- Failed page writes log to stderr and are skipped from the manifest.
- `LocalPathResolver` does not sanitize path segments.

This phase should address those debts, except for `WebCrawlerRuntime` lifecycle ownership, which may remain a documented follow-up unless it is trivial and low-risk to improve.

## Required metadata

Each mirrored document entry should include:

```text
id
url
canonicalUrl
localHtmlPath
title
depth
discoveredOrder
parentUrl
contentType
status
fetchedAt
mirrorStatus
errorMessage
````

Manifest-level metadata should include, where available:

```text
manifestVersion
startUrl
generatedAt
sameDomainOnly
maxPages
maxDepth
documentCount
successfulCount
failedCount
skippedCount
```

## Required changes

* Replace hand-crafted JSON with Jackson.
* Add or update manifest writer/reader.
* Store `localHtmlPath` relative to `outputDir`, not absolute.
* Include failed/skipped documents in the manifest or in a clearly associated mirror report.
* Add `mirrorStatus`, with at least:

    * `SUCCESS`
    * `FETCH_FAILED`
    * `WRITE_FAILED`
    * `SKIPPED`
* Sanitize `LocalPathResolver` path segments.
* Ensure resolved paths cannot escape `outputDir`.
* Add manifest round-trip tests.
* Add path traversal safety tests.

## Constraints

* Do not implement assets.
* Do not implement link rewriting.
* Do not implement PDF rendering.
* Do not implement PDF assembly.
* Keep `codex-ir-core` agnostic.
* Keep app-specific logic inside `codex-ir-app`.
* Use the full `ENGINEERING_LOG.md` reporting template.

## Validation

Run:

```bash
mvn test -pl codex-ir-app
```

Tests should verify:

* Manifest serialization.
* Manifest deserialization.
* Object → JSON → object round-trip.
* Successful page entries point to existing local files.
* Manifest paths are relative.
* Failed/skipped pages are represented.
* `LocalPathResolver` does not allow path traversal outside `outputDir`.

## Completion report

Update:

```text
docs/apps/site-exporter/ENGINEERING_LOG.md
```

Use all sections:

```text
Summary
Scope
Deliverables
Changed Files
Validation
Tests
Engineering Notes
Decisions
Tradeoffs
Risks
Known Limitations
Follow-ups
Next Step
```


---

Phase 6A — Asset Discovery and Download

Goal

Descargar assets referenciados por las páginas HTML ya espejadas, sin reescribir todavía el HTML.

Deliverables

- AssetMetadata
- AssetManifest or assets section in MirrorManifest
- AssetReferenceExtractor
- AssetDownloader
- AssetLocalPathResolver
- Asset download tests

Scope

Included:
- img[src]
- link[rel=stylesheet][href]
- script[src] if decidimos incluir JS
- same-domain assets by default
- deduplicación por canonical asset URL
- paths relativos para assets
- registro de SUCCESS / DOWNLOAD_FAILED / SKIPPED

Excluded:
- CSS url(...) discovery
- HTML rewriting
- PDF
- ePub

Validation

- Test HTML fixture with image, CSS, JS.
- Verify assets are discovered.
- Verify same asset referenced twice downloads once.
- Verify external assets are skipped or left untouched by policy.
- Verify asset paths are relative.
- Verify failed downloads are recorded.
- Verify no asset path escapes outputDir.

Yo mantendría script[src] como opcional, pero lo registraría desde ya. Para PDF probablemente CSS e imágenes importan más que JS.

Phase 6B — HTML Link Rewriting

Goal

Reescribir los HTML espejados para que el sitio sea navegable localmente.

Deliverables

- HtmlLinkRewriter
- PageLinkRewritePlan
- AssetLinkRewritePlan
- Rewritten internal page links
- Rewritten asset references
- Rewrite tests

Scope

Included:
- a[href] internos hacia páginas espejadas
- img[src] hacia assets descargados
- link[rel=stylesheet][href] hacia assets descargados
- script[src] si Phase 6A lo soportó
- external links remain external
- missing/skipped links remain unchanged or are marked in report

Excluded:
- CSS url(...) rewriting
- canonical/meta rewriting
- PDF
- ePub

Validation

- Test HTML input with internal links, external links, images, CSS.
- Verify internal links point to local relative paths.
- Verify external links remain external.
- Verify asset references point to downloaded local asset paths.
- Verify missing assets do not break rewrite.
- Verify rewritten HTML is valid enough for Jsoup round-trip.

---

## Phase 7 — Publication Pipeline Builder

Goal:

```text
Represent the export pipeline composition programmatically.
```

Example:

```java
PublicationPipeline pipeline = PublicationPipeline.builder()
    .source(SiteMirrorSource.from(mirrorDir))
    .metadata(MirrorManifest.from(manifestPath))
    .renderer(pdfRenderer)
    .assemblyStrategy(pdfAssemblyStrategy)
    .output(outputPath)
    .build();
```

Deliverables:

```text
- PublicationPipeline
- PublicationPipelineBuilder
- PublicationSource
- PublicationArtifact
- Basic validation of required components
```

Validation:

```text
- Builder tests.
- Fake renderer/fake assembly tests.
```

---

## Phase 8 — PDF Renderer Port

Goal:

```text
Introduce PDF rendering behind an interface.
```

Deliverables:

```text
- PdfRenderer interface
- PdfRenderOptions
- RenderedPdf
- OpenHtmlToPdfRenderer implementation
```

Validation:

```text
- Unit tests with fake renderer.
- Integration test or manual smoke test with OpenHTMLToPDF if feasible.
```

---

## Phase 9 — Per-page PDF Export

Goal:

```text
Render one PDF per mirrored HTML document.
```

Deliverables:

```text
- PdfExportService
- Render manifest
- One PDF per document
```

Validation:

```text
- Small mirrored test site renders to multiple PDFs.
- Failed render is reported clearly.
```

---

## Phase 10 — PDF Assembly

Goal:

```text
Combine per-page PDFs using manifest-driven order.
```

Deliverables:

```text
- PdfAssemblyStrategy
- ManifestOrderPdfAssemblyStrategy
- Combined PDF artifact
- Assembly report
```

Validation:

```text
- PDFs assembled in manifest order.
- Missing page PDF is reported.
- Empty input fails clearly.
```

````markdown
# Phase 10.8 — Resume From Existing Mirror

## Goal

Allow the publication pipeline to continue from an already downloaded mirror without crawling the site again.

## Context

The Deep Learning Book site was already mirrored successfully. Re-running the crawler is unnecessary while iterating on rendering, reader extraction, Markdown export, and PDF assembly.

The mirror directory and `mirror-manifest.json` should become reusable pipeline artifacts.

## Pipeline position

```text
Existing mirror directory
+ mirror-manifest.json
        ↓
ManifestReader
        ↓
assets / rewrite / publication
````

## Required behavior

Add a command/pipeline option such as:

```text
--skip-mirror
```

or:

```text
--from-mirror
```

When enabled:

```text
- Do not run SiteMirrorService.
- Read mirror-manifest.json from the provided mirror/output directory.
- Validate that mirror-manifest.json exists.
- Validate that SUCCESS pages with localHtmlPath point to existing local HTML files.
- Continue with the remaining pipeline stages.
```

When disabled:

```text
- Keep current behavior.
- Crawl/mirror the site.
- Write mirror-manifest.json.
- Continue pipeline.
```

## Deliverables

```text
- Command option for resume mode
- Reusable helper if needed, e.g. ExistingMirrorLoader
- Validation for mirror-manifest.json
- Tests for resume mode
- ENGINEERING_LOG.md entry
```

## Validation

```text
- Given an existing mirror directory with mirror-manifest.json, pipeline does not call SiteMirrorService.
- Missing mirror-manifest.json fails clearly.
- Manifest entry pointing to missing localHtmlPath fails clearly or is reported clearly.
- Normal mirror mode still works.
- Existing mirror mode continues to downstream stages.
```

## Constraints

```text
- Do not change crawler behavior.
- Do not change MirrorManifest schema unless strictly necessary.
- Do not re-download pages in resume mode.
- Do not implement Markdown in this phase.
```

## Notes

Prefer naming that makes the artifact explicit:

```text
--from-mirror /path/to/mirror
```

is clearer than only:

```text
--skip-mirror
```

because the pipeline needs to know where the existing mirror lives.

````

---

```markdown
# Phase 10.9 — Markdown Publication Writer

## Goal

Add a Markdown output path so the final publication slot is not PDF-only.

Markdown is likely more useful than PDF for NotebookLM ingestion, text review, future ePub generation, and debugging reader extraction.

## Context

The current pipeline can render to PDF, but the Deep Learning Book pages are generated by pdf2htmlEX. We now have a reader extraction path that can produce clean `ReaderDocument` / `ReaderPage` content.

Instead of forcing everything through PDF, add a Markdown writer that emits readable markup.

## Pipeline position

```text
MirrorManifest
        ↓
PublicationOrderingStrategy
        ↓
Reader extraction / normal text extraction
        ↓
ReaderDocument
        ↓
MarkdownPublicationWriter
        ↓
.md artifact
````

## Required behavior

Implement Markdown output from reader documents.

Minimum output:

```markdown
# <document title>

<!-- source: <url> -->

## Page <n>

Extracted paragraph...

Extracted paragraph...
```

Support:

```text
- one combined Markdown file for the publication
- page/chapter separators
- source URL comments or metadata
- stable ordering from PublicationOrderingStrategy
```

Optional but useful:

```text
- one .md per mirrored document under markdown-pages/
- combined book.md assembled from those files
```

## Deliverables

```text
- MarkdownPublicationWriter
- MarkdownExportService or equivalent helper
- Markdown output option in command/pipeline
- Combined .md artifact
- Tests for escaping/formatting
- ENGINEERING_LOG.md entry
```

## Validation

```text
- pdf2htmlEX sample exports readable Markdown.
- Output contains expected text:
  - Part I
  - Applied Math and Machine Learning Basics
  - This part of the book introduces
- Combined Markdown preserves publication order.
- Markdown output does not include scripts, CSS, base64 fonts, or positioning classes.
- Markdown file is written successfully.
```

## Constraints

```text
- Do not implement ePub yet.
- Do not remove PDF support.
- Do not make PdfAssemblyStrategy decide order.
- Do not preserve original visual layout.
- Do not include huge embedded assets/base64 content.
```

## Design note

This phase should push the architecture toward:

```text
ReaderDocument
    ↓
PublicationWriter
    ├── MarkdownPublicationWriter
    ├── PdfPublicationWriter / existing PDF path
    └── EpubPublicationWriter future
```

Markdown should be treated as a first-class publication format, not as a debug dump.

```



