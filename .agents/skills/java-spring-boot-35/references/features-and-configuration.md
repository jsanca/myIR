# Spring Boot 3.5 Features and Configuration

Boot 3.5 focuses on **operational polish** on the Spring Framework 6 baseline. Adopt features incrementally after the version bump is green.

## Servlet and Filter Registration

Prefer annotation-based registration when replacing manual `ServletRegistrationBean` boilerplate:

```java
@ServletRegistration(name = "metricsServlet", urlPatterns = "/internal/metrics/*")
public class MetricsServlet extends HttpServlet {
    // ...
}
```

```java
@FilterRegistration(name = "traceFilter", urlPatterns = "/*")
public class TraceFilter implements Filter {
    // ...
}
```

Match existing project patterns — do not mix styles without reason.

## SSL and Service Connections

3.5 extends SSL support for **service connections** (Testcontainers, Docker Compose). When tests spin up TLS-enabled dependencies, align `spring.ssl.bundle` configuration with connection properties rather than one-off trust stores.

Review actuator **SSL bundle metrics** if the app terminates TLS or uses custom keystores.

## Task Execution and Scheduling

Auto-configured executors standardize on **`applicationTaskExecutor`**. When injecting `TaskExecutor` or configuring `@Async`:

- Name custom executors explicitly when multiple exist.
- Use `@EnableAsync` with a defined `AsyncConfigurer` if the project already does — avoid duplicate bean names after upgrade.

Scheduled tasks support **task decoration** — useful for MDC/trace propagation into `@Scheduled` methods.

## Observability

OpenTelemetry export for traces and metrics is more integrated. Prefer existing Micrometer and OTel property namespaces:

```yaml
management:
  opentelemetry:
    resource-attributes:
      service.name: ${spring.application.name}
```

Enable structured logging customization only when the project already uses structured logs — do not rewrite logging architecture in a patch upgrade PR.

## WebClient and HTTP Clients

Global **WebClient** builder properties and **ClientHttpConnector** customization landed in 3.5. When adding reactive HTTP clients, reuse Boot auto-configuration beans instead of duplicating connector setup.

## Background Bean Initialization

Auto-configuration for **background bean initialization** can shorten startup in large contexts. Enable only after measuring startup time — verify thread safety of initialized beans.

## Quartz and Actuator

Quartz jobs can be triggered from the actuator when Quartz is on the classpath. Lock down actuator endpoints in production; never expose job trigger endpoints without authentication.

## Verification Commands

```bash
./gradlew clean test
./gradlew bootRun --args='--spring.profiles.active=local' &
curl -sf http://localhost:8080/actuator/health
./mvn -q verify
```

Confirm:

- Application context starts with no property binding errors
- Integration tests pass (especially HTTP client redirect behavior)
- Metrics and health endpoints respond as expected

## Related Skills

- General service work: `java-spring-boot-service`
- Java version alignment: `java-21-lts`, `java-25-lts`, `java-migrate-any-version`
- Next major line: `java-spring-boot-40`
