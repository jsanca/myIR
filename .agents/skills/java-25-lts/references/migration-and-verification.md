# Java 25 Migration and Verification

Migrate toolchain first, then adopt finalized language or library features in focused follow-ups. For cross-version planning, also see `java-migrate-any-version` references.

## Build Configuration

### Gradle (Kotlin DSL)

```kotlin
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}
```

Verify Gradle wrapper and plugin versions support Java 25 toolchains.

### Maven

```xml
<properties>
  <maven.compiler.release>25</maven.compiler.release>
</properties>
```

Update compiler plugin, Surefire, Failsafe, and annotation processor versions as needed.

## CI and Containers

GitHub Actions:

```yaml
- uses: actions/setup-java@v4
  with:
    distribution: temurin
    java-version: "25"
```

Docker:

```dockerfile
FROM eclipse-temurin:25-jre-alpine
```

Every surface must agree: local JDK (jenv, `.java-version`, SDKMAN), CI agents, build images, and production runtime.

## Migration Steps (Java 21 → 25)

1. **Toolchain only** — bump compiler release/toolchain to 25; fix compile errors from removed or restricted APIs.
2. **Dependencies** — align Spring Boot BOM, bytecode libraries, and plugins to versions that support JDK 25.
3. **Tests** — run unit and integration suites on 25; fix environment-specific failures.
4. **Runtime images** — update CI and deployment base images to Java 25.
5. **Feature adoption** — apply flexible constructors, scoped values, or module imports in separate PRs.
6. **Benchmark** — re-measure latency and throughput on performance-critical paths.

Do not combine preview-feature enablement with the baseline upgrade PR.

## Compatibility Checks

```bash
java -version
./gradlew compileJava -Xlint:deprecation
./gradlew clean test
```

```bash
java -version
mvn -q -DcompilerArgument=-Xlint:deprecation compile
mvn -q clean verify
```

Review JDK 25 migration guide for significant changes (Security Manager removal, port deprecations, GC defaults). Address compiler warnings before they become hard errors in later releases.

## Verification Matrix

| Check | Command or action | Pass criteria |
| --- | --- | --- |
| Local JDK | `java -version` | 25.x |
| Compile | `./gradlew compileJava` or `mvn compile` | No errors |
| Unit tests | `./gradlew test` or `mvn test` | All green |
| Package smoke | Run jar/container locally | Application starts on 25 |
| CI | Pipeline on Java 25 agent | Green build |
| Staging deploy | Health endpoints | Stable under load |

## Scoped Values Migration Check

When replacing `ThreadLocal`:

```java
// Before: ThreadLocal cleanup required in finally blocks
private static final ThreadLocal<String> CORRELATION = new ThreadLocal<>();

// After: immutable scoped binding per request task
public static final ScopedValue<String> CORRELATION = ScopedValue.newInstance();
```

Verify virtual-thread workloads: scoped values propagate with structured task boundaries; ensure framework integration (filters, interceptors) binds values at entry and does not leak across requests.

## What Not to Change in the Baseline Upgrade

- Do not enable `--enable-preview` for JEP 507 primitive patterns or JEP 505 structured concurrency.
- Do not add incubator module flags for Vector API unless explicitly scoped.
- Do not rewrite working Java 21 code (records, sealed types, virtual threads) unless migration exposes a concrete issue.

## Residual Risk Summary Template

After verification, report:

- JDK versions updated (build, CI, runtime)
- Dependencies still constrained to older bytecode
- Deprecated API usage remaining
- Preview features intentionally deferred
- Performance deltas observed in staging
