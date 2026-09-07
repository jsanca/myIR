# Migration to Java 21

Align toolchain and runtime first. Adopt language features in follow-up changes after tests pass cleanly on 21.

## Build Configuration

### Gradle (Kotlin DSL)

```kotlin
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}
```

### Maven

```xml
<properties>
  <maven.compiler.release>21</maven.compiler.release>
</properties>
```

Ensure the Gradle wrapper, Maven compiler plugin, and Surefire/Failsafe versions support Java 21.

## CI and Containers

GitHub Actions:

```yaml
- uses: actions/setup-java@v4
  with:
    distribution: temurin
    java-version: "21"
```

Docker:

```dockerfile
FROM eclipse-temurin:21-jre-alpine
```

Update every runtime surface: local `.java-version` or jenv, CI agents, Kubernetes base images, and deployment manifests.

## Dependency and API Compatibility

Check before merging:

- Spring Boot, Hibernate, bytecode tools, and annotation processors declare Java 21 support.
- Removed or restricted APIs flagged by `-Xlint:deprecation` are addressed or documented.
- `javax.*` vs `jakarta.*` mismatches are resolved at dependency boundaries, not with suppressions.

```bash
./gradlew compileJava -Xlint:deprecation
```

```bash
mvn -q -DcompilerArgument=-Xlint:deprecation compile
```

## Incremental Migration Steps

1. Bump compiler release/toolchain to 21 only — no source feature changes.
2. Fix compile errors from removed APIs or stricter checks.
3. Run unit and integration tests on Java 21.
4. Update CI and container images to match.
5. Adopt sequenced collections or pattern matching in focused PRs.
6. Evaluate virtual threads after observability baselines exist.

## Virtual Threads Rollout (Optional)

Keep platform-thread behavior until step 5 passes.

```java
@Bean
public TomcatProtocolHandlerCustomizer<?> virtualThreadExecutorCustomizer() {
    return protocolHandler -> protocolHandler.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
}
```

Watch thread dumps and latency under load. Roll back if pinning or third-party libraries block carrier threads.

## Verification Commands

```bash
java -version
./gradlew clean test
# or
mvn -q clean verify
```

Confirm packaged artifacts run on Java 21 in a staging environment. Re-benchmark performance-sensitive paths — behavior should match, but GC and thread scheduling can shift results slightly.

## Common Blockers

| Symptom | Likely cause | Direction |
| --- | --- | --- |
| `javax.annotation` missing | Jakarta migration incomplete | Upgrade dependency or add explicit API artifact |
| Bytecode version error | Plugin or dependency compiled for newer JDK | Align plugin versions or pin dependency |
| Test failures on 21 only | Reflection or security manager assumptions | Read release notes; remove SecurityManager usage |
| Virtual thread stall | Pinning in synchronized legacy code | Profile; keep platform threads for affected paths |
