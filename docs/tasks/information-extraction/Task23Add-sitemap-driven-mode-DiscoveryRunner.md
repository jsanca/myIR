Task 22: Add sitemap-driven mode to DiscoveryRunner

Goal:
Allow the current product discovery runner to start from a sitemap URL, fetch discovered pages, and run the existing ProductDiscoveryCollector.

Context:
The current DiscoveryRunner only accepts explicit page URLs. We now want to validate SYJ by using its sitemap as the source of URLs.

Requirements:

1. Keep this in codex-ir-app.
2. Do not change product extractors.
3. Do not change ProductDiscoveryCollector.
4. Do not add full BFS crawling yet.
5. Do not add dotCMS export yet.

Behavior:
- Support explicit URLs as today.
- Add support for:
  --sitemap https://example.com/sitemap.xml
- Fetch and parse the sitemap.
- Extract <loc> URLs.
- Support sitemap indexes if simple to add:
    - if sitemap contains nested sitemap URLs, fetch those too
    - keep a reasonable limit to avoid crawling too much
- Add a max URL limit:
  --limit 100
  default: 100
- Fetch each URL with the existing WebPageFetcher.
- Run ProductDiscoveryCollectors.jsoupDefault().
- Print the existing report.

Filtering:
- Keep only HTTP/HTTPS URLs.
- Prefer same-host URLs as the sitemap host.
- Exclude obvious non-page assets if needed.
- Do not introduce complex crawling rules.

Implementation:
- Keep sitemap parsing simple.
- Use JDK XML parsing or a simple safe parser.
- Avoid adding new dependencies unless already present.
- If parsing fails, print a clear message and exit.

Tests:
- If a small SitemapUrlExtractor/helper is introduced, add tests for:
    - simple urlset sitemap
    - sitemap index with nested sitemap locs if supported
    - limit handling
    - invalid/empty sitemap returns empty result or clear failure
- Existing tests must remain green.

Run:
mvn clean test

Report:
- files changed
- how to run with explicit URLs
- how to run with --sitemap
- sample output
- test summary