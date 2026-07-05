Please implement the JPMS/Jigsaw modularization phase for myIR.

Context:
The project has already been split into Maven modules:
- codex-ir-core
- codex-ir-web

However, these are currently Maven modules only. We now want to introduce real Java Platform Module System boundaries using module-info.java.

Goal:
Turn the current Maven multi-module structure into real JPMS modules, while keeping behavior unchanged and all tests passing.

Scope:

1. Add codex-ir-app module
    - Create a new Maven module: codex-ir-app.
    - Add it to the parent pom modules list.
    - Move codex.Main from codex-ir-web into codex-ir-app.
    - codex-ir-app should depend on codex-ir-core and codex-ir-web.
    - codex-ir-web should remain a reusable library module, not the application entry point.

2. Fix split packages before adding module-info.java
    - codex-ir-core currently has codex.ir.util.
    - codex-ir-web also has codex.ir.util with HttpUtil and UriUtil.
    - JPMS does not allow split packages across modules.
    - Move web-specific utilities to a web-specific package such as:
      codex.ir.web.util
    - Update imports accordingly.
    - Keep TermWeightingUtils in codex.ir.util inside codex-ir-core.

3. Add module-info.java to codex-ir-core
   Suggested module name:
   codex.ir.core

   Export public API packages needed by downstream modules:
    - codex.ir
    - codex.ir.corpus
    - codex.ir.corpus.vector
    - codex.ir.indexer
    - codex.ir.ranking
    - codex.ir.search
    - codex.ir.tokenizer
    - codex.ir.normalizer
    - codex.ir.vector
    - codex.ir.vector.store
    - codex.ir.weight
    - codex.ir.concurrent

   Requires:
    - org.slf4j, if needed by core production code

4. Add module-info.java to codex-ir-web
   Suggested module name:
   codex.ir.web

   Requires:
    - codex.ir.core
    - org.jsoup
    - java.net.http
    - org.slf4j, if needed by web production code

   Export:
    - codex.ir.canonicalizer
    - codex.ir.ingestion
    - codex.ir.ingestion.crawler
    - codex.ir.ingestion.crawler.fetcher
    - any new public web package if needed

   Do not export internal/helper packages unless necessary.

5. Add module-info.java to codex-ir-app
   Suggested module name:
   codex.ir.app

   Requires:
    - codex.ir.core
    - codex.ir.web

   No exports needed.

6. Review dependencies
    - codex-ir-core should not depend on jsoup or playwright.
    - codex-ir-web may depend on jsoup and playwright.
    - logback should ideally be runtime/app/test configuration, not required by core as a compile-time API dependency unless currently necessary.
    - Do not add new libraries unless required for JPMS.

7. Keep tests passing
    - Run mvn test from the root.
    - If tests remain on the classpath, that is acceptable for this phase.
    - Do not weaken module boundaries just to make tests easier.
    - Avoid opens unless actually required.

Constraints:
- No sitemap implementation yet.
- No WooCommerce extraction.
- No crawler logic refactor.
- No behavior changes.
- No new features.
- Keep package renaming minimal and only for JPMS cleanliness.
- Keep comments and documentation in English.

Expected output:
- New Maven module layout.
- List moved files/classes.
- List package renames.
- Show module-info.java files.
- Explain module dependency graph.
- Confirm there are no split packages.
- Run mvn test from root and report test counts/failures.