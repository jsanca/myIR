# Site Exporter — Engineering Log

## Phase 5 Fix — Jackson Binding Manifest Reader

### Summary

`ManifestReader` was replaced from manual `JsonNode` field-by-field extraction to a single `MAPPER.readValue(source.toFile(), MirrorManifest.class)` call. Both `MirroredPage` and `MirrorManifest` were annotated with `@JsonDeserialize(builder = ...)` and their inner `Builder` classes with `@JsonPOJOBuilder(withPrefix = "")`, making Jackson drive deserialization through the existing Builders. `ManifestWriter` was similarly collapsed to `MAPPER.writerWithDefaultPrettyPrinter().writeValue(...)`. A custom `SimpleModule` handles `Instant` serialization and deserialization (ISO-8601 strings) without requiring `jackson-datatype-jsr310`. `MirroredPage.depth` was changed from primitive `int` to nullable `Integer` and `discoveredOrder` from `int` to `long`. `SiteMirrorService` now passes `null` for depth since the crawler does not expose it; `0` is now semantically reserved for the seed/root page.

### Scope

**Included:**
- `ManifestReader` replaced with `MAPPER.readValue()` — no more `JsonNode` traversal
- `ManifestWriter` replaced with `MAPPER.writeValue()` — no more `ObjectNode` building
- `@JsonDeserialize(builder = ...)` + `@JsonPOJOBuilder(withPrefix = "")` on `MirroredPage` and `MirrorManifest`
- `@JsonIgnoreProperties(ignoreUnknown = true)` on both Builders (derived count fields in `MirrorManifest` JSON are ignored on read; counts are always recomputed from pages)
- `opens codex.apps.siteexporter to com.fasterxml.jackson.databind;` in `module-info.java` so Jackson can access private Builder constructors
- `MirroredPage.depth` → `Integer` (null = unknown, 0 = seed)
- `MirroredPage.discoveredOrder` → `long`
- `SiteMirrorService` passes `null` for depth
- 2 new round-trip tests: null depth, depth=0 distinct from null

**Excluded:** no functional changes to crawling, manifest schema, or output format.

### Deliverables

- `ManifestReader.java` — 50 lines → 45 lines; one `readValue` call
- `ManifestWriter.java` — 82 lines → 45 lines; one `writeValue` call
- `MirroredPage.java` — `@JsonDeserialize` + `@JsonPOJOBuilder` + `@JsonIgnoreProperties`; `depth: Integer`, `discoveredOrder: long`
- `MirrorManifest.java` — `@JsonDeserialize` + `@JsonPOJOBuilder` + `@JsonIgnoreProperties` on Builder
- `module-info.java` — `opens` directive
- `SiteMirrorService.java` — `depth(null)`, `discoveredOrder` as `long`
- `ManifestWriterTest.java` — 2 new tests
- `SiteMirrorServiceTest.java` — depth null assertion added

### Changed Files

| File | Change |
|---|---|
| `codex-ir-app/src/main/java/module-info.java` | Added `opens codex.apps.siteexporter to com.fasterxml.jackson.databind` |
| `codex-ir-app/src/main/java/codex/apps/siteexporter/MirroredPage.java` | `@JsonDeserialize` + `@JsonPOJOBuilder` + `@JsonIgnoreProperties`; depth→Integer; discoveredOrder→long |
| `codex-ir-app/src/main/java/codex/apps/siteexporter/MirrorManifest.java` | `@JsonDeserialize` + `@JsonPOJOBuilder` + `@JsonIgnoreProperties` on Builder |
| `codex-ir-app/src/main/java/codex/apps/siteexporter/ManifestWriter.java` | Collapsed to `MAPPER.writeValue()`; SimpleModule for Instant |
| `codex-ir-app/src/main/java/codex/apps/siteexporter/ManifestReader.java` | Collapsed to `MAPPER.readValue()`; SimpleModule for Instant |
| `codex-ir-app/src/main/java/codex/apps/siteexporter/SiteMirrorService.java` | depth(null); discoveredOrder as long |
| `codex-ir-app/src/test/java/codex/apps/siteexporter/ManifestWriterTest.java` | +2 depth round-trip tests |
| `codex-ir-app/src/test/java/codex/apps/siteexporter/SiteMirrorServiceTest.java` | assertNull(entry.depth()) |

### Validation

```
mvn test
Tests run: 50, Failures: 0, Errors: 0, Skipped: 0
```

All modules pass. `ManifestWriterTest` now has 8 tests (was 6); `SiteMirrorServiceTest` has 9 tests (unchanged count, one assertion added).

### Tests

| Test | What it verifies |
|---|---|
| `nullDepthShouldRoundTripAsNull` | A page with `depth=null` serializes to JSON with `null` depth and deserializes back to `null` |
| `depthZeroIsDistinctFromNullDepth` | `depth=0` (root page) and `depth=null` (unknown) survive round-trip as distinct values |

### Engineering Notes

- `@JsonIgnoreProperties(ignoreUnknown = true)` on `MirrorManifest.Builder` ensures the derived JSON fields (`documentCount`, `successfulCount`, `failedCount`, `skippedCount`) don't cause Jackson to fail when deserializing — they have no matching Builder setters and are intentionally ignored.
- The `opens codex.apps.siteexporter to com.fasterxml.jackson.databind;` directive is the standard JPMS + Jackson pattern. Without it, Jackson cannot call `setAccessible(true)` on the private `Builder()` constructors and throws `InaccessibleObjectException`.
- `ManifestReader` and `ManifestWriter` each own their own `ObjectMapper` instance. They have slightly different configurations (writer needs Instant serializer; reader needs Instant deserializer + `FAIL_ON_UNKNOWN_PROPERTIES=false`). Sharing a mapper was deferred as premature abstraction.
- `Instant` is handled via a `SimpleModule` rather than `jackson-datatype-jsr310` to avoid adding a new pom dependency.

### Decisions

- **`@JsonDeserialize(builder = ...)` over `@JsonCreator` on canonical constructor** — The Builder approach preserves the count-derivation logic in `MirrorManifest.Builder.build()`. Using `@JsonCreator` on the canonical constructor would pass the JSON-stored counts directly into the record, breaking the "counts derived from pages" invariant.
- **`Integer` not `int` for `depth`** — Primitive `int` cannot be null. A sentinel value like `0` or `-1` for "unknown" is semantically ambiguous since `0` is a valid depth (seed page).
- **`long` for `discoveredOrder`** — Per the task brief; `int` is sufficient for practical page counts but `long` is the safer type for a monotonically-increasing counter.

### Tradeoffs

- The `opens` directive widens Jackson's reflection access to the entire `codex.apps.siteexporter` package (not just the manifest types). This is standard practice but is broader than strictly necessary.
- Two `ObjectMapper` instances with similar but distinct configurations. The duplication is minimal and avoids a premature shared-mapper abstraction.

### Risks

- If any new type is added to `codex.apps.siteexporter` that should NOT be accessible to Jackson, the `opens` directive would still expose it. Low risk for an application module.

### Known Limitations

- `depth` and `parentUrl` are still always null at runtime (crawler doesn't expose them). The type change to `Integer` makes the nullability semantically correct but doesn't populate the field.

### Follow-ups

- Expose crawl depth from the traversal crawler to populate `MirroredPage.depth` correctly.
- Consider a shared `ManifestMapper` factory if the writer/reader configurations converge or a third caller appears.

### Next Step

**Phase 6 — Asset Download and Link Rewriting**: the manifest contract is now stable and portable. Phase 6 can consume it to locate mirrored HTML files and produce an asset inventory.

---

## Phase 5 — Robust Manifest Metadata + Manifest Hygiene

### Summary

Replaced the Phase 4 prototype manifest with a stable, portable contract. Hand-crafted JSON was replaced with Jackson. The `MirroredPage` record was expanded from 5 fields to 13. The `MirrorManifest` record was expanded from 3 fields to 11 and gains a Builder. Failed page writes now produce a `WRITE_FAILED` entry in the manifest rather than being silently dropped. `LocalPathResolver` now strips `..`/`.` segments and filesystem-unsafe characters, and verifies every resolved path stays within the output directory. `ManifestWriter` and `ManifestReader` are new public types.

### Scope

**Included:**
- Jackson-based serialization/deserialization replacing hand-crafted JSON
- Expanded `MirroredPage` schema (id, canonicalUrl, localHtmlPath as relative string, depth, discoveredOrder, parentUrl, contentType, status, fetchedAt, mirrorStatus, errorMessage)
- Expanded `MirrorManifest` schema (manifestVersion, startUrl, generatedAt, sameDomainOnly, maxPages, maxDepth, documentCount, successfulCount, failedCount, skippedCount)
- `MirrorStatus` enum: SUCCESS, FETCH_FAILED, WRITE_FAILED, SKIPPED
- `ManifestWriter` — serializes manifest to JSON file
- `ManifestReader` — deserializes manifest from JSON file
- `MirrorManifest.readFrom()` static factory for round-trip convenience
- Sanitized `LocalPathResolver`: strips `..`/`.`, replaces `[\\x00\\:*?"<>|]` with `_`, enforces stay-within-outputDir invariant
- `WRITE_FAILED` tracking in `SiteMirrorService`

**Explicitly excluded:**
- Asset manifest and download
- Link rewriting
- PDF/ePub rendering
- FETCH_FAILED and SKIPPED tracking (crawler API does not expose these events)
- `WebCrawlerRuntime` lifecycle ownership improvement

### Deliverables

- `MirrorStatus.java` — new enum
- `MirroredPage.java` — expanded record + Builder
- `MirrorManifest.java` — expanded record + Builder; delegates to ManifestWriter/Reader
- `ManifestWriter.java` — new class; Jackson-based serializer
- `ManifestReader.java` — new class; Jackson-based deserializer
- `LocalPathResolver.java` — sanitization + traversal guard
- `SiteMirrorService.java` — WRITE_FAILED tracking, relative paths, new manifest builder
- `ManifestWriterTest.java` — 6 new tests (write, round-trip, nullable fields, WRITE_FAILED, empty list, multi-page)
- `LocalPathResolverTest.java` — 7 new tests added (dot-dot stripping, dot stripping, only-dot-dot fallback, colon replace, backslash replace, traversal guard ×2)
- `SiteMirrorServiceTest.java` — updated 6 existing tests + added 3 new tests (WRITE_FAILED, relative paths, manifest config metadata)
- `codex-ir-app/pom.xml` — added `jackson-databind` dependency
- `module-info.java` — added `requires com.fasterxml.jackson.databind`

### Changed Files

| File | Change |
|---|---|
| `codex-ir-app/pom.xml` | Added `jackson-databind` dependency |
| `codex-ir-app/src/main/java/module-info.java` | Added `requires com.fasterxml.jackson.databind` |
| `codex-ir-app/src/main/java/codex/apps/siteexporter/MirrorStatus.java` | New |
| `codex-ir-app/src/main/java/codex/apps/siteexporter/MirroredPage.java` | Rewritten |
| `codex-ir-app/src/main/java/codex/apps/siteexporter/MirrorManifest.java` | Rewritten |
| `codex-ir-app/src/main/java/codex/apps/siteexporter/ManifestWriter.java` | New |
| `codex-ir-app/src/main/java/codex/apps/siteexporter/ManifestReader.java` | New |
| `codex-ir-app/src/main/java/codex/apps/siteexporter/LocalPathResolver.java` | Rewritten |
| `codex-ir-app/src/main/java/codex/apps/siteexporter/SiteMirrorService.java` | Rewritten |
| `codex-ir-app/src/test/java/codex/apps/siteexporter/ManifestWriterTest.java` | New |
| `codex-ir-app/src/test/java/codex/apps/siteexporter/LocalPathResolverTest.java` | Updated |
| `codex-ir-app/src/test/java/codex/apps/siteexporter/SiteMirrorServiceTest.java` | Updated |

### Validation

```
mvn test -pl codex-ir-app
Tests run: 48, Failures: 0, Errors: 0, Skipped: 0
```

- `SitemapUrlExtractorTest`: 8/8 (pre-existing, no regression)
- `LocalPathResolverTest`: 15/15 (8 original + 7 new sanitization/traversal tests)
- `SiteExporterCommandTest`: 6/6 (no changes needed)
- `HtmlPageWriterTest`: 4/4 (no changes needed)
- `SiteMirrorServiceTest`: 9/9 (6 updated + 3 new)
- `ManifestWriterTest`: 6/6 (new)

Manual inspection of manifest JSON output confirmed:
- `startUrl`, `generatedAt`, `documentCount`, `successfulCount`, `failedCount`, `skippedCount` present at manifest level
- Per-page `localHtmlPath` is relative (`section/page/index.html`, not an absolute path)
- `mirrorStatus` field present with value `SUCCESS` or `WRITE_FAILED`
- Failed page entries include `errorMessage` and `null` for `localHtmlPath`

### Tests

| Test class | Tests | Coverage |
|---|---|---|
| `ManifestWriterTest` | 6 new | write, round-trip SUCCESS, round-trip WRITE_FAILED, nullable fields, empty list, multi-page with mixed statuses |
| `LocalPathResolverTest` | +7 | dot-dot stripping, dot stripping, all-dot-dot fallback to index, colon replacement, backslash replacement (via %5C), traversal guard ×2 |
| `SiteMirrorServiceTest` | +3 | WRITE_FAILED tracking, relative paths, manifest config metadata (maxPages, maxDepth, sameDomainOnly, manifestVersion) |

### Engineering Notes

- `ManifestReader` recomputes `documentCount`, `successfulCount`, `failedCount`, `skippedCount` from the pages list using the Builder, ignoring the stored count fields in JSON. This ensures consistency even if the JSON was manually edited.
- `LocalPathResolver` uses `Path.toAbsolutePath().normalize()` for the traversal guard comparison, ensuring symlinks and `.` components in `outputDir` don't cause false positives.
- `SiteMirrorService.mirror()` uses `AtomicInteger` for `discoveredOrder` inside the lambda (required because lambdas can only capture effectively-final references, not mutable primitives).
- Jackson `ObjectMapper` instances in `ManifestWriter` and `ManifestReader` are static final to avoid repeated construction overhead.
- `localHtmlPath` is stored as a `String` in the record (not `Path`) to avoid platform-specific `Path.toString()` behavior. Separators are normalized to `/` at assignment time.

### Decisions

- **Jackson ObjectNode/JsonNode approach over POJO binding** — avoids the need for `@JsonProperty` on every record constructor parameter and doesn't require `jackson-module-parameter-names`. The tree-building approach is explicit and has no magic.
- **Counts computed from pages in Builder** — rather than accepting them as builder parameters, counts are always derived from the pages list. This guarantees consistency even after manual edits to the JSON.
- **MirroredPage.localHtmlPath as `String`** — storing as `String` rather than `Path` avoids `Path.toString()` returning OS-specific separators on Windows.

### Tradeoffs

- **FETCH_FAILED and SKIPPED not yet tracked** — the current `DocumentSource.readInto()` contract only delivers successfully-fetched pages. Tracking fetch failures requires a crawler API change. These statuses are defined in the enum but unused; the manifest will always show 0 for `failedCount` for fetch failures. Documented as a known limitation.
- **depth and parentUrl always 0/null** — the crawler does not expose per-page crawl depth or parent URL through the `WebPage` type. Fields are wired but set to sentinel defaults.

### Risks

- **Jackson module on module path** — `codex-ir-app` now directly requires `com.fasterxml.jackson.databind`. This is correct but means the Jackson version is fixed at the parent pom's `2.17.2`. Any upgrade must be coordinated across modules.
- **No Jackson module for Java time** — `Instant` is serialized via `Instant.toString()` and parsed back with `Instant.parse()`. This relies on ISO-8601 format consistency, which is stable, but it's manual rather than using `jackson-datatype-jsr310`. The risk is negligible for this tool.

### Known Limitations

- `FETCH_FAILED` and `SKIPPED` mirror statuses are defined but never emitted. Crawler API changes are required.
- `depth` and `parentUrl` per-page fields are always `0`/`null`. Requires crawler to surface traversal metadata.
- No live-site integration test. All tests use `@TempDir` + injected `DocumentSource`.

### Follow-ups

- Expose crawl depth and parent URL from the traversal crawler to populate those `MirroredPage` fields.
- Investigate FETCH_FAILED tracking via a failure callback in `DocumentSource` or a separate event channel.
- Consider adding `jackson-datatype-jsr310` for more robust Java time handling.

### Next Step

**Phase 6 — Asset Download and Link Rewriting**: download CSS, images, and JS referenced by mirrored HTML pages; rewrite internal links so the mirror is locally navigable. This is the prerequisite for PDF rendering from the mirrored output.

---

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
