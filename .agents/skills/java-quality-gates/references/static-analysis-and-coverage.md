# Java Static Analysis and Coverage

Trust existing config paths in the repo (`config/checkstyle/`, `spotbugs-exclude.xml`, etc.).

## Gradle — Common Plugins

```kotlin
plugins {
    id("checkstyle")
    id("jacoco")
    id("com.github.spotbugs") version "6.4.8"
    id("com.diffplug.spotless") version "6.25.0"
}

checkstyle {
    toolVersion = "10.21.0"
    configFile = file("config/checkstyle/checkstyle.xml")
    isIgnoreFailures = false
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports { xml.required.set(true) }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.jacocoTestReport)
    violationRules {
        rule {
            limit { minimum = "0.70".toBigDecimal() }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}
```

Tune thresholds per module — do not copy 70% globally without team agreement.

## Maven — Quality Plugin Pattern

```xml
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-checkstyle-plugin</artifactId>
  <executions>
    <execution>
      <id>validate</id>
      <phase>verify</phase>
      <goals><goal>check</goal></goals>
    </execution>
  </executions>
</plugin>
```

Bind SpotBugs, PMD, and JaCoCo similarly to `verify` phase.

## Checkstyle

- Keep rules in VCS under `config/checkstyle/`.
- Use suppressions file for third-party or legacy boundaries — not blanket `@SuppressWarnings` in app code.
- Fix violations in the same PR that enables the rule when possible.

## SpotBugs / PMD

- Exclude generated sources (`build/`, `target/generated-sources`).
- Treat `EI_EXPOSE_REP` and similar as team policy — document if ignored.
- Do not disable detectors globally to greenwash CI.

## JaCoCo Coverage

**Good exclusions:** generated mappers, DTO packages with no logic, main classes.

**Bad exclusions:** service or domain packages to hit a number.

Require **regression tests** for bug fixes regardless of coverage percentage.

```kotlin
// Package-level minimum example
violationRules {
    rule {
        element = "PACKAGE"
        includes = listOf("com.example.orders.*")
        limit { minimum = "0.80".toBigDecimal() }
    }
}
```

## Spotless / Formatting

```kotlin
spotless {
    java {
        googleJavaFormat("1.25.2")
        removeUnusedImports()
        trimTrailingWhitespace()
    }
}
```

Run `./gradlew spotlessApply` before commit; CI runs `spotlessCheck`.

## Enforcer (Maven) / Dependency constraints

- Ban duplicate classes and dependency convergence failures on `verify`.
- Align Spring Boot BOM — do not override managed versions without comment.

## Fixing Gate Failures

1. Read the **first** failure in log — cascading errors often share one root cause.
2. Distinguish **test failure** vs **style** vs **coverage** — do not disable all gates.
3. For legacy modules, add scoped suppression with ticket link and owner.

## Related Skills

- `java-testing-verification` — meaningful tests behind coverage numbers
- `java-spring-boot-service` — production code under test
