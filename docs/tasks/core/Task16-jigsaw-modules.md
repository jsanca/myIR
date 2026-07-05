Please prepare a modularization plan for myIR using Maven parent + Java Jigsaw modules.

Context:
myIR has grown beyond a simple IR core. We now have core IR concepts and web crawling/ingestion concepts. Before implementing the sitemap-based traversal strategy and later WordPress/WooCommerce extraction, we want to split the project into modules.

Goal:
Create a concrete, incremental modularization plan. Do not perform the full refactor yet unless explicitly requested later.

Desired module direction:
- codex-ir-core: core information retrieval engine
- codex-ir-web: web crawling, fetching, sitemap, robots, URL traversal, web ingestion
- codex-ir-woocommerce: future module, not implemented yet

Scope:

1. Inspect current packages
   Identify which packages/classes belong to:
    - core IR
    - web crawling / ingestion
    - shared utilities
    - demo/main application
    - tests

2. Propose Maven structure
   Suggested:
    - parent pom.xml
    - codex-ir-core/pom.xml
    - codex-ir-web/pom.xml
    - optional codex-ir-app or codex-ir-demo if Main should be separated

3. Propose Java module names
   Suggested:
    - codex.ir.core
    - codex.ir.web
    - codex.ir.app or codex.ir.demo

4. Define module dependencies
   Example:
    - codex.ir.core has no dependency on web
    - codex.ir.web may depend on codex.ir.core only if it maps web pages to Documents
    - codex.ir.app depends on both
    - future codex.ir.woocommerce depends on codex.ir.web and possibly codex.ir.core

5. Identify package moves
   For each existing package, recommend:
    - keep in core
    - move to web
    - move to app/demo
    - leave for later

6. Identify risks
   Include:
    - package-private access breakage
    - test refactoring
    - module-info exports
    - dependency cycles
    - resources/fixtures
    - Maven Surefire/JUnit module path issues

7. Recommend incremental execution
   Prefer small steps:
    - create parent
    - create core module
    - move only core first
    - make tests pass
    - create web module
    - move crawling packages
    - make tests pass
    - add app/demo module if needed

8. Do not implement sitemap traversal yet
   This plan is preparation for implementing SitemapSiteTraversalStrategy after the module boundaries are clear.

Constraints:
- Do not implement WooCommerce extraction.
- Do not implement SitemapSiteTraversalStrategy yet.
- Do not rewrite crawler logic.
- Do not refactor ranking/indexing behavior.
- Do not add new dependencies unless required by module structure.
- Preserve all existing tests.
- Keep comments and documentation in English.

Expected output:
- Proposed Maven module structure
- Proposed Java module names
- Package/class inventory
- Dependency graph
- Incremental migration plan
- Risks and mitigations
- Recommendation for where SitemapSiteTraversalStrategy should live