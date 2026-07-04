# Phase 9.5 — Extract Publication Ordering Strategy

## Context

`PublicationPipeline.run()` currently renders pages in the order returned by `MirrorManifest.pages()`.

That works today because the manifest order reflects crawler discovery order, but ordering is a separate concern from rendering and PDF assembly.

We want `PdfAssemblyStrategy` to remain responsible only for joining already-ordered PDF byte arrays. It should not decide page order.

## Goal

Introduce an explicit ordering strategy between manifest reading and PDF rendering.

Pipeline concept:

```text
MirrorManifest
    ↓
PublicationOrderingStrategy
    ↓
ordered MirroredPage list
    ↓
PdfRenderer
    ↓
PdfAssemblyStrategy
````

## Required changes

1. Add a new interface:

```java
public interface PublicationOrderingStrategy {
    List<MirroredPage> order(MirrorManifest manifest);
}
```

or equivalent.

2. Add a default implementation:

```java
DiscoveredOrderPublicationOrderingStrategy
```

Behavior:

```text
- Include only SUCCESS pages with non-null localHtmlPath.
- Sort by discoveredOrder ascending.
```

3. Update `PublicationPipeline.Builder` to accept an optional ordering strategy.

Default:

```text
DiscoveredOrderPublicationOrderingStrategy
```

4. Update `PublicationPipeline.run()`:

Current responsibility:

```text
filter SUCCESS pages directly from manifest.pages()
```

New responsibility:

```text
ask orderingStrategy.order(source.manifest())
```

Then render the ordered list.

5. Keep `PdfAssemblyStrategy` unchanged.

It should only receive already-rendered pages in order.

## Tests

Add/update tests for:

```text
- default ordering strategy uses discoveredOrder ascending
- WRITE_FAILED pages are excluded
- pages with null localHtmlPath are excluded
- PublicationPipeline uses ordering strategy result, not raw manifest order
- custom ordering strategy can reverse page order
- PdfAssemblyStrategy receives pages in strategy order
```

## Constraints

Do not implement:

```text
- TOC ordering
- heuristic ordering
- PDF assembly changes
- CLI changes
- ePub
```

## Engineering log

Update:

```text
docs/apps/site-exporter/ENGINEERING_LOG.md
```

Add a section:

```markdown
## Phase 9.5 — Publication Ordering Strategy

### Summary
### Scope
### Deliverables
### Changed Files
### Validation
### Tests
### Engineering Notes
### Decisions
### Tradeoffs
### Risks
### Known Limitations
### Follow-ups
### Next Step
```

## Completion report

Report:

```text
- ordering strategy interface added
- default discovered-order strategy added
- pipeline now delegates ordering
- tests run
- any limitations
```
