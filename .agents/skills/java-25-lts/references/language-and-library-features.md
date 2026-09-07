# Java 25 Language and Library Features (Finalized)

Document only **finalized** features below. Do not adopt preview or incubator APIs unless the repository explicitly enables `--enable-preview` or incubator modules with team approval.

## Finalized Language Features (Java 25)

| Feature | JEP | Notes |
| --- | --- | --- |
| Module Import Declarations | 511 | Import all exported packages from a module with one declaration |
| Compact Source Files and Instance Main Methods | 512 | Simplified entry points for scripts and small tools |
| Flexible Constructor Bodies | 513 | Statements before explicit `super(...)` or `this(...)` |

### Module Import Declarations (JEP 511)

Use when many types from the same module are referenced and imports are noisy. Prefer ordinary imports when only a few types are needed.

```java
import module java.base;

public final class IdParser {

    public UUID parse(String raw) {
        Objects.requireNonNull(raw, "raw");
        return UUID.fromString(raw.trim());
    }

    public List<UUID> parseAll(Collection<String> values) {
        return values.stream().map(this::parse).toList();
    }
}
```

Module imports do not replace dependency management in Maven or Gradle — they simplify source imports for on-module types.

### Flexible Constructor Bodies (JEP 513)

Validate inputs and initialize fields before calling another constructor.

```java
public class Employee extends Person {

    private final String badgeId;

    public Employee(String name, String badgeId) {
        Objects.requireNonNull(badgeId, "badgeId");
        if (badgeId.isBlank()) {
            throw new IllegalArgumentException("badgeId must not be blank");
        }
        this.badgeId = badgeId;
        super(name);
    }

    public String badgeId() {
        return badgeId;
    }
}
```

Statements before `super(...)` or `this(...)` must not reference the instance under construction (no virtual method calls). Field initialization of the current class is allowed.

### Compact Source Files and Instance Main Methods (JEP 512)

Relevant for CLI utilities and learning examples — not typical Spring service layout.

```java
void main() {
    System.out.println("Health check stub OK");
}
```

Keep standard `public class` + `public static void main` for production services unless the repo already uses compact files intentionally.

## Finalized Library and Runtime Features

### Scoped Values (JEP 506)

Prefer over `ThreadLocal` for immutable, structured request context — especially with virtual threads.

```java
public final class RequestContext {

    public static final ScopedValue<String> CORRELATION_ID = ScopedValue.newInstance();

    public static String correlationId() {
        return CORRELATION_ID.orElse("unknown");
    }

    public static void runWithCorrelation(String correlationId, Runnable action) {
        ScopedValue.where(CORRELATION_ID, correlationId).run(action);
    }
}
```

Usage at a request boundary:

```java
public void handle(String correlationId, Runnable work) {
    RequestContext.runWithCorrelation(correlationId, work);
}
```

Scoped values are immutable bindings per scope; they do not replace mutable per-request bags without careful design.

### Key Derivation Function API (JEP 510)

Use platform KDF APIs instead of ad-hoc password stretching when deriving keys.

```java
import javax.crypto.KDF;
import javax.crypto.spec.HKDFParameterSpec;

public final class TokenKeyDerivation {

    public SecretKey deriveAesKey(byte[] inputKeyMaterial, byte[] salt, byte[] info) {
        Objects.requireNonNull(inputKeyMaterial, "inputKeyMaterial");
        Objects.requireNonNull(salt, "salt");
        Objects.requireNonNull(info, "info");

        KDF hkdf = KDF.getInstance("HKDF-SHA256");
        AlgorithmParameterSpec params = HKDFParameterSpec.ofExtract()
            .addIKM(inputKeyMaterial)
            .addSalt(salt)
            .thenExpand(info, 32);

        return hkdf.deriveKey("AES", params);
    }
}
```

Confirm algorithm names and provider availability in target runtime before switching from existing crypto libraries.

## Preview and Incubator — Do Not Adopt by Default

| Feature | JEP | Status in 25 |
| --- | --- | --- |
| Primitive types in patterns, `instanceof`, and switch | 507 | Third preview — requires `--enable-preview` |
| Structured concurrency | 505 | Fifth preview |
| Stable values | 502 | Preview |
| PEM encodings of cryptographic objects | 470 | Preview |
| Vector API | 508 | Tenth incubator — requires `--add-modules` |

Mention these only when the user explicitly asks about preview features. Do not recommend enabling preview in production services without a documented team policy.

## Practical Adoption Order

1. Toolchain and runtime on 25 with existing source unchanged.
2. Flexible constructor bodies where validation currently duplicates logic across constructors.
3. Scoped values where `ThreadLocal` causes lifecycle or virtual-thread concerns.
4. Module import declarations in modules with heavy `java.base` usage — optional readability win.
5. Defer preview/incubator features to dedicated experiments.
