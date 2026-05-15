Please implement Phase 2 of the myIR Maven modularization plan.

Goal:
Create the codex-ir-web Maven module and move the existing web/crawling/ingestion packages into it, while keeping behavior unchanged and all tests passing.

Current state:
The project already has a Maven parent with one child module:
- codex-ir-core

Phase 1 is complete:
- src/ was moved into codex-ir-core/src
- all package names are unchanged
- mvn test from root passes with 155 tests

Scope:

1. Create codex-ir-web module
    - Add codex-ir-web/pom.xml.
    - Add codex-ir-web to the parent pom modules list.
    - codex-ir-web should depend on codex-ir-core.
    - Move web-only dependencies such as jsoup/playwright-related dependencies from codex-ir-core to codex-ir-web if they are only used by web packages.

2. Move web-related production packages from codex-ir-core to codex-ir-web
   Move:
    - codex.ir.canonicalizer
    - codex.ir.ingestion
    - codex.ir.ingestion.crawler
    - codex.ir.ingestion.crawler.fetcher

3. Split codex.ir.util
   Keep in codex-ir-core:
    - TermWeightingUtils

   Move to codex-ir-web:
    - HttpUtil
    - UriUtil

   Do not rename packages yet unless required. Prefer minimal movement first.

4. Move related tests
   Move web-related tests to codex-ir-web, especially:
    - UriCanonicalizersTest

   If there are crawler/ingestion tests already present, move them too.
   Leave all pure IR tests in codex-ir-core.

5. Keep code behavior unchanged
    - Do not refactor crawler logic.
    - Do not implement sitemap traversal.
    - Do not implement WooCommerce extraction.
    - Do not add module-info.java yet unless absolutely necessary.
    - Do not rename packages unless required to fix split-package problems.

6. Build and test from root
    - Run mvn test from the root parent.
    - Ensure both modules pass.

Constraints:
- No production behavior changes.
- No new features.
- No package renaming unless required.
- No JPMS module-info.java yet.
- No sitemap implementation yet.
- No WooCommerce extraction yet.
- Keep comments and documentation in English.

Expected output:
- New module layout.
- List moved packages/files.
- Explain dependency changes.
- Confirm codex-ir-core no longer depends on web-specific libraries if possible.
- Report mvn test from root, including test count and failures.