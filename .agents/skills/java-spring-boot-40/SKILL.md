---
name: java-spring-boot-40
description: Migrate applications to Spring Boot 4.0 and Spring Framework 7. Use when upgrading from Boot 3.5, planning a major Spring migration, adopting Jackson 3, JSpecify nullness, Jakarta EE 11 baselines, Security 7, Hibernate 7, or modular Boot starters.
---

# Java Spring Boot 4.0

Spring Boot **4.0** is a **major** release: Spring Framework **7**, Jakarta EE **11**, **Java 17+** (Java 21/25 recommended), **Jackson 3**, and stricter null-safety with **JSpecify**. Upgrade from **3.5** first when possible.

## Workflow

1. Confirm Java **17+** everywhere (build, CI, containers). Prefer **21** or **25** when the team can align toolchains.
2. Inventory current Boot **3.5.x**, Spring Security, Hibernate, Jackson, Tomcat/Jetty, and test dependencies from build files.
3. Read `references/migration-from-35.md` for the ordered migration plan and breaking-change checklist.
4. Read `references/framework7-and-dependencies.md` for Framework 7, Jackson 3, JSpecify, persistence, and security defaults.
5. Bump Boot to **4.0.x** (latest patch), resolve dependency management, and fix compile errors in small commits.
6. Replace deprecated patterns — `WebSecurityConfigurerAdapter`, javax imports, Jackson 2-only APIs, Spring `@Nullable`/`@NonNull` where JSpecify is required.
7. Run unit, slice, and integration tests; fix Security filter chains, serialization, and Hibernate schema behavior explicitly.
8. Verify observability, actuator, native image (if used), and deployment images on the target servlet container (Tomcat 11+, Jetty 12.1+).

## References

- Read `references/migration-from-35.md` for step-by-step 3.5 → 4.0 migration and verification.
- Read `references/framework7-and-dependencies.md` for Spring Framework 7, Jackson 3, JSpecify, JPA/Hibernate 7, and Security 7 details.

## Migration Checklist

- Boot **3.5.x** baseline reached before 4.0 (or document why a direct jump is unavoidable).
- Java 17+ on compiler, CI, and runtime; no mixed JDK in the pipeline.
- All `jakarta.*` imports; no `javax.servlet`, `javax.persistence`, or `javax.validation` in application code.
- Jackson 3 on the classpath; JSON serializers/deserializers and `ObjectMapper` customization updated.
- JSpecify nullness annotations where the codebase or libraries expect them; latent NPE paths reviewed.
- Security uses `SecurityFilterChain` beans; OAuth/resource-server config validated against Security 7.
- Hibernate 7 / JPA 3.2 behavior checked for schema generation, auditing, and lazy-loading changes.
- Test stack aligned with project BOM (JUnit 5 or 6 per dependency management — follow the repo).
- Undertow not assumed — Boot 4 drops Undertow support; use Tomcat or Jetty.
- Custom auto-configuration and `spring.factories`/`AutoConfiguration.imports` updated for modular Boot 4 layout.

## Output

After Boot 4.0 migration work, summarize:

- Versions changed (Boot, Framework, Java, containers, CI)
- Breaking changes hit and how they were resolved
- Deferred items (library upgrades, nullness sweep, API versioning adoption)
- Tests run and results
- Rollback or feature-flag strategy if applicable
