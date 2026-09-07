---
type: Engineering Log Entry
title: "Phase 7 — Publication Pipeline Builder"
description: "Canonical engineering evidence for Phase 7 — Publication Pipeline Builder."
tags: [site-exporter, engineering-log, migration]
timestamp: 2026-07-05T00:00:00Z
ckf_version: "0.1"
ckf_status: completed
ckf_scope: site-exporter
ckf_owner: project
---
# Phase 7 — Publication Pipeline Builder

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
