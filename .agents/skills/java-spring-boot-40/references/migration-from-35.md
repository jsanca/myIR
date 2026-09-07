# Migration from Spring Boot 3.5 to 4.0

Follow the official [Spring Boot 4.0 migration guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide) and [release notes](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Release-Notes) for the authoritative list.

## Prerequisites

| Requirement | Minimum |
| --- | --- |
| Spring Boot | **3.5.x** on a green test suite |
| Java | **17+** (21 or 25 recommended) |
| Namespace | Jakarta throughout (`jakarta.*`) |
| Servlet container | Tomcat 11+ or Jetty 12.1+ for deployment targets |

Do not migrate application code to Boot 4 while still on Java 11 or Boot 3.3/3.4 without an intermediate upgrade plan.

## Version Bump

Gradle (Kotlin DSL):

```kotlin
plugins {
    id("org.springframework.boot") version "4.0.7"
    id("io.spring.dependency-management") version "1.1.7"
}
```

Maven:

```xml
<parent>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-parent</artifactId>
  <version>4.0.7</version>
</parent>
```

Use the latest **4.0.x** patch from the project's repository.

## Recommended Migration Order

1. **Java baseline** — ensure JDK 17+ in build toolchain, CI, and Docker images (`java-migrate-any-version` if needed).
2. **Boot 3.5** — land on 3.5.x with passing tests if not already there (`java-spring-boot-35`).
3. **Dependency audit** — list starters, Spring Cloud, third-party auto-config, and internal libraries for Boot 4 compatibility.
4. **Boot 4 BOM** — bump parent/BOM; run compile; capture all errors before fixing behavior.
5. **Jackson 3** — update custom modules, `ObjectMapper` beans, and JSON tests (see `framework7-and-dependencies.md`).
6. **Security** — revisit `SecurityFilterChain`, resource server, CSRF, and OAuth client auto-configuration.
7. **Persistence** — validate Hibernate 7 schema tools, converters, and `@Transactional` boundaries.
8. **Nullness** — adopt JSpecify where compiler or library contracts require it; fix warnings incrementally.
9. **Tests** — update slice annotations, mock APIs, and test dependency versions.
10. **Runtime** — deploy to target container; smoke health, auth, and critical APIs.

Prefer **incremental PRs** (BOM only → Jackson → Security → domain fixes) over a single mega-commit.

## Common Breaking Areas

| Area | Action |
| --- | --- |
| Modular Boot artifacts | Starter coordinates may have moved — use BOM-managed deps, not copied 3.x coordinates |
| Jackson 3 | Package and API changes — fix compile errors in DTOs, tests, and `@Json*` usage |
| JSpecify | Replace `org.springframework.lang.Nullable` / `@NonNull` where migration warnings appear |
| Undertow | Switch to Tomcat or Jetty |
| Properties | Run with properties migrator temporarily; fix renamed/removed keys |
| Actuator | Re-check endpoint exposure and security rules |
| `@ConfigurationProperties` | Stricter binding — fix typos surfaced as failures |

Temporary helper (remove before merge):

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-properties-migrator</artifactId>
  <scope>runtime</scope>
</dependency>
```

## Planning Output (before coding)

When asked to plan only, produce:

- Current vs target versions
- Risk-ranked module list (Security, Jackson, JPA, custom auto-config)
- Suggested PR sequence and rollback point
- Test strategy (unit, slice, contract, smoke)

## Verification

```bash
./gradlew clean check
./mvn -q verify
curl -sf http://localhost:8080/actuator/health
```

Run integration and contract tests that exercise JSON serialization and authenticated endpoints.

## Related Skills

- Stay on 3.x line: `java-spring-boot-35`
- Patch upgrades on 4.x: `java-spring-boot-41`
- General services: `java-spring-boot-service`
- Cross-version Java: `java-migrate-any-version`
