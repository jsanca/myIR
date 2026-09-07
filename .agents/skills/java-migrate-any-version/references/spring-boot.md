# Spring Boot Alignment During Java Upgrades

Upgrade Java and Spring Boot in coordinated steps. Read the project's BOM before guessing versions.

## Version Detection

Gradle:

```kotlin
// build.gradle.kts
plugins {
    id("org.springframework.boot") version "3.4.1"
}
```

Maven:

```xml
<parent>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-parent</artifactId>
  <version>3.4.1</version>
</parent>
```

## Boot 3.x vs 4.x Skills

| Target | Skill |
| --- | --- |
| Spring Boot 3.5 (final 3.x) | `java-spring-boot-35` |
| Spring Boot 4.0 migration | `java-spring-boot-40` |
| Spring Boot 4.1 on 4.0 baseline | `java-spring-boot-41` |
| General service implementation | `java-spring-boot-service` |

## Boot 3 vs Boot 4 Quick Reference

| Topic | Boot 3.x | Boot 4.x |
| --- | --- | --- |
| Namespace | Jakarta (`jakarta.*`) | Jakarta EE 11 |
| Spring Framework | 6.x | 7.x |
| Migration docs | Spring Boot 3 migration guide | Spring Boot 4 release notes + Framework 7 changes |
| Test stack | JUnit 5 default | Follow project BOM (may include JUnit 6 where configured) |

Always trust the repository's `build.gradle`, `pom.xml`, or BOM over generic examples.

## javax to jakarta Scan

```bash
rg "import javax\\.(.persistence|validation|servlet)" src/
```

Replace with Jakarta equivalents or upgrade libraries that still emit `javax` types.

## Configuration Properties Migration

Spring Boot 3+ removed many deprecated properties. Check startup warnings:

```text
Property 'spring.redis.host' is deprecated
```

Use `spring-boot-properties-migrator` temporarily during upgrades:

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-properties-migrator</artifactId>
  <scope>runtime</scope>
</dependency>
```

## Security Configuration

Prefer component-based security (`SecurityFilterChain` bean) over deprecated adapter patterns when touching security during migration.

```java
@Bean
SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    return http
        .csrf(csrf -> csrf.disable())
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/actuator/health").permitAll()
            .anyRequest().authenticated())
        .build();
}
```

## Verification After Boot + Java Change

```bash
./gradlew bootRun --args='--spring.profiles.active=local' &
curl -sf http://localhost:8080/actuator/health
./gradlew test
```

Confirm health, smoke API calls, and integration tests before merge.
