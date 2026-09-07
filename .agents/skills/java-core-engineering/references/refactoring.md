# Refactoring and Code Review Examples

Preserve behavior first. Add or adjust tests before structural changes when risk is non-trivial.

## Replace Magic Strings with Typed Values

Before:

```java
if ("ACTIVE".equals(user.getStatus())) {
    sendWelcomeEmail(user);
}
```

After:

```java
public enum AccountStatus { ACTIVE, SUSPENDED, CLOSED }

if (user.status() == AccountStatus.ACTIVE) {
    sendWelcomeEmail(user);
}
```

## Narrow Exception Handling

Before:

```java
try {
    parser.parse(input);
} catch (Exception ex) {
    log.warn("parse failed");
}
```

After:

```java
try {
    parser.parse(input);
} catch (JsonProcessingException ex) {
    throw new InvalidPayloadException("Request body is not valid JSON", ex);
}
```

## Extract Method to Clarify Intent

Before:

```java
public boolean eligible(Driver driver) {
    return driver.getAge() >= 21
        && driver.getViolations().stream().noneMatch(v -> v.severity() >= 3)
        && driver.getLicenseExpiry().isAfter(LocalDate.now());
}
```

After:

```java
public boolean eligible(Driver driver) {
    return meetsMinimumAge(driver)
        && hasNoSeriousViolations(driver)
        && hasValidLicense(driver);
}
```

## Review Checklist With Code Smells

| Smell | Example | Preferred direction |
| --- | --- | --- |
| God class | 800-line service doing validation, IO, mapping | Split by responsibility |
| Primitive obsession | `String status` everywhere | Enum or value object |
| Leaking mutability | Returning internal `ArrayList` | `List.copyOf` or unmodifiable view |
| Boolean trap | `createUser(true, false, true)` | Named parameters or builder |
| Stream overkill | 12-step pipeline in one expression | Extract steps or use loop |

## Verification Commands

Gradle:

```bash
./gradlew test --tests "com.example.core.*"
```

Maven:

```bash
mvn -q test -Dtest=CoreRefactorTest
```
