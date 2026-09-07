---
type: Engineering Log Entry
title: "Phase 5 Fix — Jackson Binding Manifest Reader"
description: "Canonical engineering evidence for Phase 5 Fix — Jackson Binding Manifest Reader."
tags: [site-exporter, engineering-log, migration]
timestamp: 2026-07-05T00:00:00Z
ckf_version: "0.1"
ckf_status: completed
ckf_scope: site-exporter
ckf_owner: project
---
# Phase 5 Fix — Jackson Binding Manifest Reader

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
