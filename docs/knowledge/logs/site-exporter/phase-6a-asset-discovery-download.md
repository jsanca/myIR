---
type: Engineering Log Entry
title: "Phase 6A — Asset Discovery and Download"
description: "Canonical engineering evidence for Phase 6A — Asset Discovery and Download."
tags: [site-exporter, engineering-log, migration]
timestamp: 2026-07-05T00:00:00Z
ckf_version: "0.1"
ckf_status: completed
ckf_scope: site-exporter
ckf_owner: project
---
# Phase 6A — Asset Discovery and Download

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
