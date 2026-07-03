# Site Exporter Test Plan

## Goal

Define the test cases and evaluator checklist for the `site-exporter` app before implementation starts.

The app will reuse the existing myIR traversal crawler to mirror a website to local disk, then transform the mirrored content into publication artifacts such as PDF and, later, ePub.

## Architectural Invariants

### Core isolation

* `codex-ir-core` must not depend on site exporter classes.
* `codex-ir-core` must not depend on PDF, ePub, OpenHTMLToPDF, or publication-specific libraries.
* `codex-ir-web` must not depend on site exporter classes.
* `codex-ir-app` may depend on `codex-ir-web`.

Expected evaluator result:

```text
PASS if site-exporter remains an application boundary.
FAIL if PDF/export logic leaks into core or reusable web primitives.
```

---

## Mirror Tests

### LocalPathResolver

Given a remote URL, the resolver must produce a stable safe local path.

Cases:

```text
https://example.com/                         -> index.html
https://example.com/about                    -> about/index.html or about.html
https://example.com/about.html               -> about.html
https://example.com/docs/chapter-1.html      -> docs/chapter-1.html
https://example.com/search?q=java&page=2     -> deterministic safe path
https://example.com/a/b/../c                 -> normalized safe path
```

Assertions:

* No path traversal.
* No absolute local paths.
* Same canonical URL maps to same local path.
* Different URLs do not accidentally overwrite each other.

---

### MirrorManifest

The mirror manifest must record enough metadata for later export and assembly.

Required document metadata:

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

Assertions:

* `discoveredOrder` is stable within one run.
* Every mirrored HTML document has one manifest entry.
* Every manifest entry points to an existing local file.
* Failed or skipped pages are represented or reported clearly.

---

### SiteMirrorService

Given a small test website, the service must mirror HTML pages to disk.

Assertions:

* Start URL is downloaded.
* Same-host links are followed when enabled.
* External links are not downloaded when `sameHostOnly=true`.
* `maxPages` is respected.
* Non-HTML resources are not treated as documents.
* The output directory contains HTML files and `mirror-manifest.json`.

---

## Link Rewriting Tests

Given mirrored pages, internal links should be rewritten to local paths.

Cases:

```html
<a href="/chapter1.html">
<a href="chapter2.html">
<a href="https://example.com/chapter3.html">
<a href="https://external.com/page.html">
<img src="/images/logo.png">
<link rel="stylesheet" href="/css/site.css">
```

Assertions:

* Internal page links point to local mirrored HTML paths.
* External links remain external.
* Asset references point to local asset paths when assets are mirrored.
* Unknown or skipped links are handled gracefully.

---

## Asset Tests

When asset downloading is enabled:

Assertions:

* CSS files are downloaded.
* Images are downloaded.
* Duplicate assets are not downloaded multiple times unnecessarily.
* Asset paths are safe and deterministic.
* Asset metadata is recorded in the manifest or asset manifest.

---

## PDF Renderer Port Tests

The PDF renderer must be behind an interface.

Assertions:

* Export pipeline depends on `PdfRenderer`, not directly on OpenHTMLToPDF.
* `OpenHtmlToPdfRenderer` is one implementation of the port.
* A fake `PdfRenderer` can be used in tests.
* Renderer failures are reported without corrupting the whole export manifest.

---

## Per-page PDF Export Tests

Given a mirrored site with N HTML documents:

Assertions:

* One PDF is produced per renderable HTML document.
* Output PDFs use stable names based on document ids or local paths.
* Render output is recorded in a render manifest.
* Failed pages are reported clearly.

---

## PDF Assembly Tests

Given several per-page PDFs and manifest metadata:

Assertions:

* `ManifestOrderPdfAssemblyStrategy` assembles PDFs in manifest order.
* Missing PDFs are reported clearly.
* Empty input fails with a meaningful error.
* Final artifact path is returned.
* Assembly report includes included/skipped documents.

---

## Publication Pipeline Builder Tests

The builder should make the pipeline composition explicit.

Assertions:

* Required components are validated.
* Missing source fails fast.
* Missing renderer fails fast for render operations.
* Missing assembly strategy fails fast for combined artifacts.
* The pipeline can be constructed with fake components in tests.
* The pipeline does not hardcode PDF as the only future format.

---

## CLI / App Tests

Expected commands:

```bash
site-mirror --start-url=... --output-dir=... --max-pages=...
site-export --mirror-dir=... --format=pdf --output=...
```

Assertions:

* Invalid arguments fail clearly.
* Output directory is created if missing.
* Existing output behavior is explicit.
* Commands print enough information to debug a run.
* Commands do not silently ignore failures.

---

## Evaluator Checklist

Elito should verify:

```text
1. Boundaries are preserved.
2. The traversal crawler is reused instead of duplicated.
3. The mirror pipeline is a sink/pipeline variation, not a new crawler.
4. Manifest metadata is sufficient for later export.
5. PDF rendering is behind a port.
6. Assembly is strategy-based.
7. Tests cover local path safety, manifest correctness, and assembly ordering.
8. ENGINEERING_LOG.md contains the full report sections.
9. Validation commands are documented.
10. Known limitations and follow-ups are explicit.
```
