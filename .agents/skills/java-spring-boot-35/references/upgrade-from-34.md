# Upgrade from Spring Boot 3.4 to 3.5

Always trust the repository build file and [Spring Boot 3.5 release notes](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-3.5-Release-Notes) over generic examples.

## Version Bump

Gradle (Kotlin DSL):

```kotlin
plugins {
    id("org.springframework.boot") version "3.5.14"
    id("io.spring.dependency-management") version "1.1.7"
}
```

Maven:

```xml
<parent>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-parent</artifactId>
  <version>3.5.14</version>
</parent>
```

Use the latest **3.5.x** patch available in the project's artifact repository.

## Breaking and Behavioral Changes

| Area | What to check |
| --- | --- |
| `spring-boot-parent` | No longer published — remove if referenced directly; use `spring-boot-starter-parent` or Gradle BOM import |
| Boolean `.enabled` properties | Only `true` or `false` accepted — fix loose values like `yes`, `1`, empty strings |
| Profile names | Letters, digits, `-`, `_` only; no leading/trailing `-` or `_` |
| `TestRestTemplate` | Redirect behavior aligned with `RestTemplate`; review integration tests that assumed old redirect defaults |
| Actuator `heapdump` | Default access is more restrictive — confirm exposure in non-prod only |
| Prometheus Pushgateway | Configuration property changes — verify metrics export config |

## Upgrade Steps

1. Bump Boot parent/BOM and Spring dependency-management plugin.
2. Run `./gradlew dependencies --configuration compileClasspath` or `mvn dependency:tree` and resolve version conflicts.
3. Start the app locally; capture **CONDITIONS EVALUATION REPORT** and deprecation warnings.
4. Add `spring-boot-properties-migrator` temporarily if many renamed properties appear:

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-properties-migrator</artifactId>
  <scope>runtime</scope>
</dependency>
```

5. Fix tests, then remove the migrator dependency.
6. Re-run CI with the same Java version used in production (17–25 supported on 3.5).

## javax / jakarta

Boot 3.x is Jakarta-only. If `javax.persistence` or `javax.validation` imports remain, fix libraries or source — this is not new in 3.5 but often surfaces during upgrades.

```bash
rg "import javax\\.(persistence|validation|servlet)" src/
```

## When Not to Stop at 3.5

If the goal is Spring Framework 7, Jackson 3, or JSpecify nullness, plan **Boot 4.0** instead — use the `java-spring-boot-40` skill after reaching 3.5.
