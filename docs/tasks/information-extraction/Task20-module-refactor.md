Task: Refactor myIR module/package boundaries after crawler and product extraction work

Context:

The project is starting to separate into clearer architectural areas:

codex.ir.core
→ tokenization, indexing, ranking, corpus, search

codex.ir.ingestion.web
→ fetch, URL classification, crawling, WebPage, metadata

codex.ir.ingestion.extraction
→ ProductDetailExtractor, ProductCardExtractor, ProductDiscoverer, JSON-LD, OpenGraph, HTML extraction heuristics

codex.ir.export
→ CSV, JSON, dotCMS import candidates

We are not necessarily creating all physical Maven modules in this task unless the current project structure makes it simple and safe. The main goal is to clean boundaries and prepare the codebase for this direction without breaking behavior.

Goals:

1. Review current Maven modules and Java modules:
    - root pom
    - codex-ir-core
    - codex-ir-web
    - codex-ir-app
    - all module-info.java files
    - current package exports

2. Preserve the current working build:
    - Do not change behavior.
    - Do not add new product extraction heuristics.
    - Do not add new crawling behavior.
    - Do not add export functionality yet.
    - This is a structure/boundary cleanup task only.

3. Validate the intended conceptual boundaries:

   codex.ir.core should contain:
    - tokenization
    - normalization
    - documents/corpus
    - index structures
    - ranking
    - searching
    - IR statistics and core search abstractions

   codex.ir.ingestion.web should contain:
    - WebPage
    - fetchers
    - crawler abstractions
    - URL classification
    - URL filters
    - page classification
    - page metadata extraction
    - web-specific ingestion utilities

   codex.ir.ingestion.extraction should eventually contain:
    - ProductDetailExtractor
    - ProductCardExtractor
    - ProductDiscoverer
    - ProductDiscoveryResult
    - ProductDetail/ProductCard/ProductImage/ProductPrice if appropriate
    - JSON-LD extraction
    - OpenGraph extraction
    - HTML product extraction heuristics

   codex.ir.export should eventually contain:
    - CSV export
    - JSON export
    - dotCMS import/export candidate models
    - external output formatting

4. For this task, prefer a conservative approach:
    - If creating a new Maven/JPMS module for extraction is too invasive, do not do it yet.
    - Instead, prepare package boundaries inside codex-ir-web.
    - Make the code ready for a future extraction module split.
    - Avoid large package moves unless they clearly reduce coupling and are easy to validate.

5. Review current public API packages:
    - classifier
    - filter
    - metadata
    - product
    - internal/classifier
    - internal/metadata
    - internal/product

   Ensure:
    - public API packages are intentionally exported
    - internal packages are not exported
    - implementation classes remain internal or package-private
    - public factory classes stay thin where reasonable

6. Review package naming:
    - Keep URL/page classification separate from URL filtering.
    - Keep metadata extraction separate from product extraction.
    - Keep product extraction separate from crawler orchestration as much as possible.
    - Do not move core IR concepts into web/extraction packages.
    - Do not move web-specific concepts into codex-ir-core.

7. Review module dependencies:
    - codex-ir-core should not depend on codex-ir-web.
    - codex-ir-core should not know about crawling, Jsoup, product extraction, or web metadata.
    - codex-ir-web may depend on codex-ir-core only where needed.
    - codex-ir-app can wire modules together.
    - Internal packages should not leak through exported APIs unless intentional.

8. If safe, introduce package-level preparation for future extraction split:
    - Keep product extraction code grouped cleanly.
    - Avoid product extraction depending unnecessarily on crawler implementation classes.
    - If ProductDiscoverer depends on PageClassifier and WebPage, keep that dependency explicit and understandable.
    - Do not introduce a new module unless it is low-risk.

9. Check module-info.java carefully:
    - Add exports only for true public APIs.
    - Do not export internal packages.
    - Ensure requires clauses are minimal and accurate.
    - Remove stale requires/exports if any exist.
    - Confirm Jsoup dependency is only in modules/packages that actually parse HTML.

10. Tests:
- Update package imports if anything moves.
- Do not weaken tests.
- Do not delete tests unless they are obsolete due only to package renaming.
- Existing behavior must remain green.

11. Documentation:
- If there is a package/module overview document, update it.
- If not, add a short developer note explaining the intended direction:

  codex.ir.core
  → tokenization, indexes, ranking, corpus, search

  codex.ir.ingestion.web
  → fetch, URL classification, crawling, WebPage, metadata

  codex.ir.ingestion.extraction
  → product extraction, JSON-LD, OpenGraph, HTML heuristics

  codex.ir.export
  → CSV, JSON, dotCMS import candidates

12. Run:
    mvn clean test

13. Report:
- Files changed
- Whether new modules were created or only packages were cleaned
- Current exported packages
- Current internal/non-exported packages
- Any dependency/module inconsistencies found and fixed
- Test result summary