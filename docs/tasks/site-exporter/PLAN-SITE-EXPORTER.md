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

## Phase 6 — Asset Download and Link Rewriting

Goal:

```text
Make the mirrored site navigable locally.
```

Deliverables:

```text
- AssetDownloader
- HtmlLinkRewriter
- AssetMetadata
- Rewritten internal links
- Rewritten CSS/image references
```

Validation:

```text
- Test HTML input with links/assets.
- Verify local rewritten output.
- Verify external links remain external.
```

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

---

## Phase 11 — Future Format Readiness

Goal:

```text
Prepare for ePub without implementing it yet.
```

Deliverables:

```text
- PublicationFormat abstraction reviewed.
- Clear extension point for ePub renderer/package strategy.
- Follow-up task documented.
```

Validation:

```text
- No PDF-only concepts leak into generic publication pipeline.
```

---

## First Vertical Slice Target

The first real implementation milestone should be:

```text
URL → traversal crawler → local HTML files → mirror-manifest.json
```

Out of scope for the first slice:

```text
- assets
- link rewriting
- PDF
- ePub
- PDF assembly
```

The first slice should be intentionally small, testable, and reviewable.
