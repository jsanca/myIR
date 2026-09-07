# Java Idioms and Patterns

Prefer clarity over cleverness. Match the project's Java version before introducing newer language features.

## Records for Immutable Data Carriers

Use records for value objects with stable, read-only shape.

```java
public record MoneyAmount(BigDecimal value, Currency currency) {
    public MoneyAmount {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(currency, "currency");
        if (value.signum() < 0) {
            throw new IllegalArgumentException("value must be non-negative");
        }
    }
}
```

## Sealed Types for Exhaustive Domain Modeling

Use sealed hierarchies when a closed set of variants is a business rule.

```java
public sealed interface PaymentResult permits PaymentAccepted, PaymentDeclined, PaymentPending {
}

public record PaymentAccepted(String authorizationId) implements PaymentResult {
}

public record PaymentDeclined(String reasonCode) implements PaymentResult {
}

public record PaymentPending(String reference) implements PaymentResult {
}

public String messageFor(PaymentResult result) {
    return switch (result) {
        case PaymentAccepted accepted -> "Accepted: " + accepted.authorizationId();
        case PaymentDeclined declined -> "Declined: " + declined.reasonCode();
        case PaymentPending pending -> "Pending: " + pending.reference();
    };
}
```

## Optional: Boundaries, Not Fields

Use `Optional` for return types where absence is meaningful. Avoid `Optional` fields and method parameters unless the API already uses them.

```java
// Good: return type expresses absence
public Optional<User> findByEmail(String email) {
    return repository.findByEmail(email);
}

// Avoid: Optional as a field
public class Profile {
    private Optional<String> nickname; // prefer nullable or explicit empty sentinel
}
```

## Intentional Exceptions

Catch specific exceptions. Preserve cause. Do not swallow.

```java
public Invoice loadInvoice(UUID id) {
    try {
        return repository.findById(id)
            .orElseThrow(() -> new InvoiceNotFoundException(id));
    } catch (DataAccessException ex) {
        throw new InvoiceLoadException("Failed to load invoice " + id, ex);
    }
}
```

## Readable Streams

Keep stream pipelines short. Extract named methods when logic branches.

```java
public List<String> activeCustomerEmails(List<Customer> customers) {
    return customers.stream()
        .filter(Customer::active)
        .map(Customer::email)
        .filter(email -> !email.isBlank())
        .distinct()
        .sorted()
        .toList();
}
```

## Immutability and Defensive Copies

Return unmodifiable views or copies when exposing internal collections.

```java
public List<LineItem> lineItems() {
    return List.copyOf(items);
}
```
