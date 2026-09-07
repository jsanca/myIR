# Spring Framework 7 and Boot 4 Dependencies

Boot 4.0 aligns the ecosystem on **Spring Framework 7** and **Jakarta EE 11**. Always read the project's BOM before applying generic snippets.

## Stack Overview

| Component | Boot 3.5 (typical) | Boot 4.0 (typical) |
| --- | --- | --- |
| Spring Framework | 6.2.x | 7.0.x |
| Jakarta EE | 10 | 11 |
| Jackson | 2.x | 3.x |
| Servlet | 6.0 | 6.1 |
| JPA / Hibernate | 3.1 / 6.x | 3.2 / 7.x |
| Bean Validation | 3.0 | 3.1 |
| Spring Security | 6.x | 7.x |
| Nullness | Spring lang annotations | JSpecify preferred |

## Jackson 3

- Confirm Jackson 3 packages on the classpath after the Boot bump.
- Update custom `Module` registrations, `JsonSerializer`/`JsonDeserializer` implementations, and test fixtures.
- Re-run contract tests and OpenAPI-generated clients — JSON shape changes often come from default typing or date formatting, not just package renames.
- Do not mix Jackson 2 and 3 in the same application context.

Example — prefer Boot auto-configured `JsonMapper` beans over manual `new ObjectMapper()` when possible.

## JSpecify Nullness

Framework 7 and Boot 4 emphasize **JSpecify** (`org.jspecify.annotations.Nullable`, `@NonNull`).

- Fix public API contracts on services and controllers when migration surfaces nullness warnings.
- Do not add blanket `@NonNull` without reading call sites — fix behavior, not only annotations.
- Prefer optional types or explicit guards where null is a valid domain state.

## Web and Servlet Baseline

- Deploy on **Tomcat 11+** or **Jetty 12.1+** when not using embedded defaults.
- Use **`jakarta.servlet.*`** exclusively.
- **API versioning** support is stable in Boot 4 — adopt when adding new public HTTP surfaces, matching team conventions.

## Spring Security 7

Keep component-based configuration:

```java
@Bean
SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    return http
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/actuator/health").permitAll()
            .anyRequest().authenticated())
        .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
        .build();
}
```

Re-test CSRF, CORS, session, and OAuth flows — defaults may differ from Security 6.

## Persistence (Hibernate 7 / JPA 3.2)

- Validate DDL generation, Flyway/Liquibase ordering, and `@Entity` mapping against Hibernate 7.
- Review `@Embeddable`, converters, and bytecode enhancement settings.
- For custom `PersistenceUnitInfo` bootstrapping, use `SpringPersistenceUnitInfo` patterns from Framework 7 docs — avoid deprecated SPI wiring.

## Testing

Follow the **project BOM** for JUnit versions. Boot 4 ecosystems may pull JUnit 6 where configured — do not force JUnit 6 if the repo still standardizes on JUnit 5 unless upgrading tests is in scope.

Prefer existing slice tests:

- `@WebMvcTest` for controllers
- `@DataJpaTest` for repositories
- `@SpringBootTest` only when cross-layer behavior requires it

## Modular Spring Boot

Boot 4 modularizes artifacts into smaller JARs. Symptoms of incorrect dependencies:

- ClassNotFoundException for internal Boot classes
- Missing auto-configuration

Fix by using official starter coordinates from the **4.0 BOM**, not copied 3.x GAV strings.

## Container and Native Images

- Base images must ship Java 17+.
- GraalVM native image metadata format changed in recent JDK/Graal releases — re-run native builds after upgrade if the project uses AOT.

## Detection Commands

```bash
rg "import javax\\.(servlet|persistence|validation)" src/
rg "com\\.fasterxml\\.jackson" src/    # Jackson 2 packages — migrate to Jackson 3 equivalents
rg "org\\.springframework\\.lang\\.(Nullable|NonNull)" src/
```

## Related Skills

- `java-security-hardening` — production security review after Security 7 changes
- `java-persistence-performance` — Hibernate tuning after ORM upgrade
- `java-testing-verification` — broader test strategy
