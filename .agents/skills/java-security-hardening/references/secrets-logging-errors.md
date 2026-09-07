# Secrets, Logging, and Error Responses

## Secrets and Configuration

Load secrets from the environment or a secret manager. Never commit them.

```java
@ConfigurationProperties(prefix = "payments")
public record PaymentProperties(
    @NotBlank String apiBaseUrl,
    @NotBlank String apiKey
) {
}
```

```yaml
# application.yml — reference env vars, not literal secrets
payments:
  api-base-url: ${PAYMENTS_API_BASE_URL}
  api-key: ${PAYMENTS_API_KEY}
```

```java
// Unsafe — hardcoded credential
private static final String API_KEY = "sk_live_abc123";
```

If a secret appears in source control, assume it is compromised and must be rotated.

## Safe Logging

Log identifiers and outcomes, not secrets or regulated personal data.

```java
// Unsafe
log.info("Authenticating user email={} password={}", email, password);

// Safer
log.info("Authentication attempt for userId={}", userId);

// When correlation is needed without exposing PII
log.warn("Payment failed correlationId={} reasonCode={}", correlationId, reasonCode);
```

Use structured fields and consistent correlation IDs. Do not log full request bodies for auth or payment endpoints unless explicitly approved and redacted.

## Safe API Error Responses

Return actionable messages to clients without internal details.

```java
@RestControllerAdvice
public class SecurityAwareExceptionHandler {

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied() {
        return ProblemDetail.forStatusAndDetail(
            HttpStatus.FORBIDDEN,
            "Access denied");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unhandled error", ex);
        return ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "An unexpected error occurred");
    }
}
```

Do not return stack traces, SQL fragments, file paths, or secret configuration values in HTTP responses, toast messages, or client-visible audit entries.

## Dependency and Build Hygiene

- Run dependency vulnerability scans (`./gradlew dependencyCheckAnalyze`, OWASP Dependency-Check, or platform equivalent).
- Pin and review changes to security-sensitive libraries (Spring Security, JWT, Bouncy Castle, Apache HttpClient).
- Keep the JVM and framework on supported versions with security patches applied.
