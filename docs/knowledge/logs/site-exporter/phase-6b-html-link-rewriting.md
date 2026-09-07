---
type: Engineering Log Entry
title: "Phase 6B — HTML Link Rewriting"
description: "Canonical engineering evidence for Phase 6B — HTML Link Rewriting."
tags: [site-exporter, engineering-log, migration]
timestamp: 2026-07-05T00:00:00Z
ckf_version: "0.1"
ckf_status: completed
ckf_scope: site-exporter
ckf_owner: project
---
# Phase 6B — HTML Link Rewriting

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
