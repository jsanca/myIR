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

## Phase 5 — Robust Manifest Metadata

Goal:

```text
Make the manifest useful for later publication assembly.
```

Required document fields:

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
```

Deliverables:

```text
- MirrorManifest
- DocumentMetadata
- Manifest writer/reader
- Manifest tests
```

Validation:

```text
- Unit tests for manifest serialization.
- Manifest entries point to existing files.
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
