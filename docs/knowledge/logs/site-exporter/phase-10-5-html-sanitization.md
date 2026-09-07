---
type: Engineering Log Entry
title: "Phase 10.5 — HTML Sanitization Before PDF Rendering"
description: "Canonical engineering evidence for Phase 10.5 — HTML Sanitization Before PDF Rendering."
tags: [site-exporter, engineering-log, migration]
timestamp: 2026-07-05T00:00:00Z
ckf_version: "0.1"
ckf_status: completed
ckf_scope: site-exporter
ckf_owner: project
---
# Phase 10.5 — HTML Sanitization Before PDF Rendering

### Summary

Introduced `HtmlToXhtmlSanitizer` to convert raw mirrored HTML to well-formed XHTML before handing it to OpenHTMLToPDF, which requires XML-compliant input. Without this step, real-world pages with bare `<br>`, `<meta>`, unclosed `<p>` or `<img>` tags fail to render. `OpenHtmlToPdfRenderer` now reads the file, sanitizes it via Jsoup XML output mode, then passes the clean XHTML string to `PdfRendererBuilder.withHtmlContent()`.

### Scope

**Included:**
- `HtmlToXhtmlSanitizer` — Jsoup parse + XML/XHTML output settings
- `OpenHtmlToPdfRenderer` — now sanitizes before rendering; accepts injected sanitizer for tests
- Tests for sanitizer (12) and renderer with malformed HTML (5 new)

**Excluded:**
- Crawler changes
- Manifest changes
- Assembly changes
- Playwright
- CSS url(...) rewriting
- Inlining external resources

### Deliverables

- `HtmlToXhtmlSanitizer.java` — new class
- `HtmlToXhtmlSanitizerTest.java` — 12 tests
- Updated `OpenHtmlToPdfRenderer.java` — sanitizer field + package-private constructor for injection
- Updated `OpenHtmlToPdfRendererTest.java` — 5 new malformed-HTML round-trip tests

### Changed Files

| File | Change |
|---|---|
| `codex-ir-app/src/main/java/codex/apps/siteexporter/HtmlToXhtmlSanitizer.java` | Created |
| `codex-ir-app/src/test/java/codex/apps/siteexporter/HtmlToXhtmlSanitizerTest.java` | Created |
| `codex-ir-app/src/main/java/codex/apps/siteexporter/OpenHtmlToPdfRenderer.java` | Updated |
| `codex-ir-app/src/test/java/codex/apps/siteexporter/OpenHtmlToPdfRendererTest.java` | Updated |

### Validation

```
mvn test -pl codex-ir-web,codex-ir-app
Tests run: 174, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

- `HtmlToXhtmlSanitizerTest` — 12 tests, 0 failures
- `OpenHtmlToPdfRendererTest` — 10 tests (was 5; added 5 malformed-HTML tests), 0 failures

### Tests

| Test class | Tests | What is covered |
|---|---|---|
| `HtmlToXhtmlSanitizerTest` | 12 | Null guards; bare `<br>`; bare `<meta charset>`; bare `<img>`; bare `<link>`; unclosed `<p>` tags; leading whitespace before doctype; leading HTML comments; missing html/head/body structure; text content preserved |
| `OpenHtmlToPdfRendererTest` (new) | 5 | Bare `<meta charset>`; bare `<br>` tags; unclosed `<p>` tags; leading whitespace + doctype; mixed void elements — all produce `%PDF` output |

### Engineering Notes

- Jsoup `Document.OutputSettings.Syntax.xml` makes Jsoup serialize void elements as self-closing (`<br />`, `<meta ... />`) and ensures all non-void elements are explicitly closed. This is exactly what OpenHTMLToPDF expects.
- `escapeMode(Entities.EscapeMode.xhtml)` ensures HTML entities are output in XHTML-compatible form.
- `charset(StandardCharsets.UTF_8)` keeps the output UTF-8 regardless of platform default.
- The `baseUri` is passed to `Jsoup.parse()` for in-document link resolution during parsing. It is not written into the output string; it is passed separately to `withHtmlContent()` for OpenHTMLToPDF asset resolution.
- `OpenHtmlToPdfRenderer` gains a package-private constructor `OpenHtmlToPdfRenderer(HtmlToXhtmlSanitizer)` to allow injection in tests without changing the public API.
- One test needed a fix during development: the original `sanitizeShouldSelfCloseImgElement` checked `!result.contains("<img src")`, which is always false because `<img src="..." />` also starts with `<img src`. Fixed to check `!result.contains("alt=\"logo\">")` (the bare closing `>` after attributes).

### Decisions

- Used Jsoup rather than a DOM/SAX transformer. Jsoup is already on the classpath and handles real-world broken HTML far better than standard XML parsers.
- Sanitizer is injected into `OpenHtmlToPdfRenderer` via a package-private constructor rather than making `HtmlToXhtmlSanitizer` an interface. The sanitizer has one correct implementation and the injection point is only for tests, so the full Interface+Factory pattern is not warranted.

### Tradeoffs

| Choice | Alternative | Reason |
|---|---|---|
| Jsoup XML output mode | Manual regex cleanup | Jsoup handles all the edge cases (implicit closing, entity encoding, attribute quoting) correctly |
| Package-private constructor for test injection | Public constructor or mocking | Avoids leaking test-only API surface while still allowing injection |
| Pass `baseUri` to both Jsoup and OpenHTMLToPDF | Pass only to OpenHTMLToPDF | Gives Jsoup accurate context for relative URL normalization during parsing |

### Risks

- Jsoup's HTML5 parser may alter some elements (e.g., move `<style>` from body to head, fix table structure). For PDF output this is generally harmless, but it means the XHTML passed to OpenHTMLToPDF may differ structurally from the original HTML.
- Very large HTML files will be held as a string in memory twice (raw + XHTML). This is acceptable for typical mirrored pages.

### Known Limitations

- Inline CSS `url(...)` references remain unmodified — relative asset paths inside stylesheets still depend on the `baseUri` being set correctly.
- External stylesheets referenced by relative `href` are resolved by OpenHTMLToPDF using the `baseUri`, which points to the local mirror directory. This works only if the assets were downloaded in Phase 6A.

### Follow-ups

- Phase 11: review `PublicationFormat` abstraction for ePub readiness.
- Consider adding a charset-detection step before sanitization for pages that declare a non-UTF-8 charset.
- Log sanitizer warnings (e.g., Jsoup parse error count) into the `AssemblyReport`.

### Next Step

Phase 11 — Future Format Readiness: review `PublicationFormat` abstraction, confirm no PDF-only concepts leak into the generic pipeline, and document the ePub extension point.

---
