---
type: Engineering Log Entry
title: "Phase 3 — Site Exporter Skeleton"
description: "Canonical engineering evidence for Phase 3 — Site Exporter Skeleton."
tags: [site-exporter, engineering-log, migration]
timestamp: 2026-07-05T00:00:00Z
ckf_version: "0.1"
ckf_status: completed
ckf_scope: site-exporter
ckf_owner: project
---
# Phase 3 — Site Exporter Skeleton

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
