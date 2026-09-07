# Gradle Migration Checks

Prefer Gradle toolchains over machine-global `JAVA_HOME` assumptions.

## Java Toolchain

```kotlin
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}
```

## Wrapper and Plugin Compatibility

```properties
# gradle/wrapper/gradle-wrapper.properties
distributionUrl=https\\://services.gradle.org/distributions/gradle-8.12-bin.zip
```

Verify Android, Kotlin, and Spring plugins against the target JDK before rollout.

## Test Task JVM Alignment

```kotlin
tasks.test {
    useJUnitPlatform()
}

tasks.withType<Test>().configureEach {
    maxParallelForks = Runtime.getRuntime().availableProcessors().div(2).coerceAtLeast(1)
}
```

## CI Cache and Container Image

Ensure CI uses the same major JDK as local toolchains:

```yaml
- uses: actions/setup-java@v4
  with:
    distribution: temurin
    java-version: '21'
```

## Verification Commands

```bash
./gradlew -version
./gradlew clean test
./gradlew build
```

Compare `./gradlew -version` JVM with `java -version` in deployment images.
