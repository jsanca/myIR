---
name: java-spring-boot-35
description: Upgrade to or implement features on Spring Boot 3.5 (final 3.x release). Use when moving from 3.4 to 3.5, adopting 3.5 configuration and observability improvements, fixing 3.5 breaking changes, or staying on Spring Framework 6 before a Boot 4 migration.
---

# Java Spring Boot 3.5

Spring Boot **3.5** is the last **3.x** minor line (Spring Framework **6.2**, Java **17+**). Use it when the project must stay on Framework 6 or when preparing a later move to Boot 4.0.

## Workflow

1. Read the project's `build.gradle`, `build.gradle.kts`, or `pom.xml` and confirm current Spring Boot, Java, and plugin versions.
2. If upgrading from **3.4**, read `references/upgrade-from-34.md` first for breaking changes and property renames.
3. Bump the Boot BOM or parent to **3.5.x** (latest patch), align Spring dependency-management plugins, and run compile plus tests.
4. Fix startup warnings: deprecated properties, tightened `.enabled` boolean values, profile naming rules, and actuator access changes.
5. Adopt 3.5 features only where they solve a concrete problem — servlet/filter registration annotations, SSL service connections, structured logging tweaks, OpenTelemetry export, async executor naming.
6. Do **not** introduce Boot 4 / Framework 7 APIs (Jackson 3, JSpecify migration, modular starter renames) unless the build already targets 4.x.
7. Verify health, smoke APIs, integration tests, and observability exporters after the upgrade.

## References

- Read `references/upgrade-from-34.md` for breaking changes when upgrading from 3.4.
- Read `references/features-and-configuration.md` for notable 3.5 capabilities, configuration patterns, and verification commands.

## Adoption Checklist

- Boot version is **3.5.x** on Spring Framework 6.2; Java baseline is 17 or higher.
- No dependency on unpublished `spring-boot-parent` (removed in 3.5).
- `.enabled` properties use strict `true`/`false`; profile names follow tightened rules.
- Actuator exposure reviewed (`heapdump` defaults stricter).
- Task executor bean names and async configuration match 3.5 auto-configuration.
- Tests and CI run against the same Boot and Java versions as production.

## Output

After Boot 3.5 work, summarize:

- Versions changed (Boot BOM, plugins, Java, containers, CI)
- Breaking changes addressed from the 3.4 → 3.5 path
- New 3.5 features adopted and why
- Tests run and results
- Follow-ups for a future Boot 4.0 migration, if any
