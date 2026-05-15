Please implement Phase 1 of the myIR Maven modularization plan.

Goal:
Create the Maven parent structure and move the current project into codex-ir-core with minimal behavior changes.

Scope:
1. Create root parent pom.xml with packaging pom.
2. Create codex-ir-core module.
3. Move the current src directory into codex-ir-core/src.
4. Move the current production and test code as-is.
5. Keep existing package names unchanged.
6. Keep all current tests in codex-ir-core for now, including web/crawler tests if moving them later belongs to Phase 2.
7. Adjust codex-ir-core/pom.xml so the project compiles and tests run from the root.
8. Do not create codex-ir-web yet unless absolutely required.
9. Do not move crawler/ingestion packages yet.
10. Do not add module-info.java yet unless the current project already has one and it must be preserved.
11. Do not implement sitemap traversal yet.
12. Do not implement WooCommerce extraction.

Constraints:
- No behavior changes.
- No package renaming.
- No production refactoring.
- No new features.
- Keep all tests passing.
- Keep comments and documentation in English.

Expected output:
- List changed files/directories.
- Explain the new Maven parent/module layout.
- Confirm all tests run from the root with mvn test.
- Report test count and failures.