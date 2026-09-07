---
name: java-migrate-any-version
description: Plan and execute Java version migrations. Use when upgrading Java source, runtime, build tools, dependencies, Spring Boot versions, CI images, removed APIs, compiler settings, or test strategy across Java versions.
---

# Java Migrate Any Version

## Workflow

1. Detect current Java source, target, runtime, build tool, dependency versions, and CI image versions.
2. Identify the target Java version and whether the upgrade includes framework or container changes.
3. Read the relevant migration path reference before changing code.
4. Upgrade build configuration first, then dependencies, then source incompatibilities.
5. Run tests after each meaningful migration step.
6. Verify runtime behavior, packaging, CI, Docker images, and deployment configuration.

## References

- Read `references/11-to-17.md` for Java 11 to 17 migrations.
- Read `references/17-to-21.md` for Java 17 to 21 migrations.
- Read `references/21-to-25.md` for Java 21 to 25 migrations.
- Read `references/maven.md` for Maven migration checks.
- Read `references/gradle.md` for Gradle migration checks.
- Read `references/spring-boot.md` for Spring Boot version alignment during Java upgrades.
- Read `references/testing-strategy.md` for test and CI verification during migrations.

## Output

After each migration step, summarize:

- Current and target Java, build tool, and framework versions
- Configuration files changed
- Source or dependency fixes applied
- Tests run and results
- Remaining blockers, deprecated API usage, or CI/container updates needed
