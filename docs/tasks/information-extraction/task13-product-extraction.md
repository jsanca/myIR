We are continuing the myIR project.

The product extraction work is complete and green. The next step is to add a page metadata extraction layer.

Goal:
Create a small, focused metadata extraction component for HTML pages using Jsoup.

It should extract:
- title
- meta description
- canonical URL
- Open Graph metadata
- Twitter card metadata
- robots meta directives
- main headings h1/h2
- page language when available
- JSON-LD blocks as raw structured metadata for now

Architectural constraints:
- Keep extraction components small and testable.
- Prefer internal extractor classes and public-facing aggregate/facade only if needed.
- Do not mix crawling, indexing, and extraction responsibilities.
- Do not over-engineer yet.
- Follow the style of the recent product extraction refactor.
- Add unit tests for each extractor.
- Keep comments in English.
- Do not rewrite existing product extraction logic unless necessary.

Suggested package:
codex.ir.web.internal.metadata

Suggested types:
- PageMetadata
- PageMetadataExtractor
- MetaTagExtractor
- OpenGraphExtractor
- TwitterCardExtractor
- RobotsMetaExtractor
- HeadingExtractor
- JsonLdBlockExtractor

Expected result:
- Clean implementation
- Tests passing
- No regressions