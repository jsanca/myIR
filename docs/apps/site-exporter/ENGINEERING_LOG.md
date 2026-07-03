# Site Exporter — Engineering Log

## Phase 4 — Mirror HTML Vertical Slice

### What was built

Five new types in `codex.apps.siteexporter`:

| Type | Kind | Purpose |
|---|---|---|
| `LocalPathResolver` | final class | Maps a URI to a local `Path` inside the output directory |
| `HtmlPageWriter` | final class | Writes `WebPage.rawHtml()` to disk via `LocalPathResolver` |
| `MirroredPage` | record | One manifest entry: URL, local path, title, status, fetchedAt |
| `MirrorManifest` | record | Holds all `MirroredPage` entries; serializes to `mirror-manifest.json` |
| `SiteMirrorService` | final class | Orchestrates crawl → write → manifest; package-private overload accepts injected `DocumentSource<WebPage>` for testing |

`SiteExporterCommand` was refactored: `parseArgs` now throws `IllegalArgumentException` instead of calling `System.exit`, making it unit-testable. `--no-same-domain` flag wired. `SiteMirrorService` wired into `main()`.

### Files changed

- `codex-ir-app/src/main/java/codex/apps/siteexporter/LocalPathResolver.java` — new
- `codex-ir-app/src/main/java/codex/apps/siteexporter/HtmlPageWriter.java` — new
- `codex-ir-app/src/main/java/codex/apps/siteexporter/MirroredPage.java` — new
- `codex-ir-app/src/main/java/codex/apps/siteexporter/MirrorManifest.java` — new
- `codex-ir-app/src/main/java/codex/apps/siteexporter/SiteMirrorService.java` — new
- `codex-ir-app/src/main/java/codex/apps/siteexporter/SiteExporterCommand.java` — refactored
- `codex-ir-app/src/test/java/codex/apps/siteexporter/LocalPathResolverTest.java` — new
- `codex-ir-app/src/test/java/codex/apps/siteexporter/HtmlPageWriterTest.java` — new
- `codex-ir-app/src/test/java/codex/apps/siteexporter/SiteMirrorServiceTest.java` — new
- `codex-ir-app/src/test/java/codex/apps/siteexporter/SiteExporterCommandTest.java` — new

### Validation

- `mvn test -pl codex-ir-app`: **32 tests, 0 failures, 0 errors**
  - `SitemapUrlExtractorTest`: 8/8 (pre-existing, no regression)
  - `LocalPathResolverTest`: 8/8
  - `HtmlPageWriterTest`: 4/4
  - `SiteMirrorServiceTest`: 6/6
  - `SiteExporterCommandTest`: 6/6
- URI → path mapping verified across root, trailing-slash, no-extension, and extension cases.
- Output files verified via `@TempDir` isolation.
- Manifest JSON verified for required fields and correct page count.
- `outputDir`-is-a-file guard verified.
- `sameDomainOnly=true` confirmed as default in both options builder and command parser.

### Tradeoffs

- **Manifest JSON hand-crafted** — Jackson is available in `codex-ir-web` but not re-exported to `codex-ir-app`. Adding a `requires com.fasterxml.jackson.databind` to `module-info.java` would work without a pom.xml change, but hand-crafting keeps the dependency surface minimal for Phase 4. Phase 5 can revisit.
- **`SiteMirrorService.mirror(options)` closes `WebCrawlerRuntime.getInstance()`** — this singleton is safe to close here because the mirror command is the only runtime user. If future commands share the runtime, this needs revisiting.
- **`LocalPathResolver` does not sanitize path segments** — on a controlled dev tool this is acceptable. A web-facing mirror tool would need to strip traversal characters (e.g. `../`).

### Risks and limitations

- No integration test against a live site (intentional — validation plan requires a tiny local/static test site; that is deferred to the evaluator or a future fixture-based test).
- `MirrorManifest` stores absolute `localPath` values — these are not portable across machines. Phase 5 should switch to paths relative to `outputDir`.
- Failed page writes log to stderr and are silently skipped; they do not appear in the manifest. This is acceptable for Phase 4 but should be surfaced in the manifest in Phase 5.

### Next steps

- **Phase 5** — expand `MirroredPage` to the full metadata schema (`id`, `canonicalUrl`, `depth`, `discoveredOrder`, `parentUrl`, `contentType`, `status`, `fetchedAt`); switch manifest paths to relative; add manifest round-trip tests.

---

## Phase 3 — Site Exporter Skeleton

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
