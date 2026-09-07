# Upgrade from Spring Boot 4.0 to 4.1

4.1 is a **minor** upgrade on the Boot 4 platform. Read [Spring Boot 4.1 release notes](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.1-Release-Notes) for the full change list.

## Prerequisites

- Application stable on **Boot 4.0.x** with passing tests
- Java **17+** (21/25 recommended)
- Third-party BOMs (Spring Cloud, etc.) declare Boot 4.1 compatibility

## Version Bump

Gradle (Kotlin DSL):

```kotlin
plugins {
    id("org.springframework.boot") version "4.1.0"
    id("io.spring.dependency-management") version "1.1.7"
}
```

Maven:

```xml
<parent>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-parent</artifactId>
  <version>4.1.0</version>
</parent>
```

Use the latest **4.1.x** patch available to the project.

## Upgrade Steps

1. Bump Boot parent/BOM to 4.1.x.
2. Align Spring Cloud or internal platform BOMs — resolve enforcer or dependency conflicts before code changes.
3. Run `./gradlew clean check` or `mvn verify`.
4. Scan release notes for deprecated properties removed between 4.0 and 4.1.
5. Smoke-test authenticated APIs, gRPC endpoints (if any), and observability backends.

## What Usually Changes

| Area | Typical impact |
| --- | --- |
| Spring Framework | Patch/minor within 7.0.x line |
| Spring Security | 7.1 — re-test OAuth, CSRF, CORS |
| Spring Session | 4.1 line when session modules are used |
| Jackson | Updated configuration property names — see `new-features.md` |
| OpenTelemetry | Exporter/propagator defaults — verify traces in staging |

## When to Stay on 4.0

- Platform BOM blocks 4.1
- Vendor runtime not yet certified for 4.1
- Short maintenance window — take latest **4.0.x** patch instead

## Related Skills

- Major migration: `java-spring-boot-40`
- Final 3.x line: `java-spring-boot-35`
- General REST/service work: `java-spring-boot-service`
