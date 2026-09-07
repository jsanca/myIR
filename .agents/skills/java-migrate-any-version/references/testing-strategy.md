# Migration Testing Strategy

Run the smallest useful test scope after each migration step. Expand only when shared infrastructure changed.

## Step-by-Step Verification

| Step | Change | Test command |
| --- | --- | --- |
| 1 | Build file JDK/toolchain | `./gradlew compileJava` or `mvn -q compile` |
| 2 | Dependency upgrades | `./gradlew test --tests "*Smoke*"` |
| 3 | Source fixes | Module-specific test package |
| 4 | Packaging / image | `./gradlew bootJar` + container smoke |
| 5 | CI pipeline | Full `./gradlew build` or `mvn verify` |

## Regression Suite Order

```bash
# Fast signal
./gradlew test --tests "com.example.smoke.*"

# Module most affected by migration
./gradlew :core:test

# Full suite before merge
./gradlew build
```

## CI Matrix During Migration PRs

Run both old and new JDK only while dual-support is required:

```yaml
strategy:
  matrix:
    java: [17, 21]
steps:
  - uses: actions/setup-java@v4
    with:
      java-version: ${{ matrix.java }}
  - run: ./gradlew test
```

Remove old JDK from matrix once production runtime cutover completes.

## Test Failure Triage

| Failure type | Likely migration cause |
| --- | --- |
| `NoClassDefFoundError: javax.*` | Missed Jakarta dependency or old library |
| Mockito inline / byte-buddy errors | Test dependency not compatible with new JDK |
| Testcontainers startup failure | Docker image or Ryuk compatibility |
| Security test 401/403 changes | Security filter chain or property rename |
| Date/time assertion drift | Zone or clock assumptions in tests |

## Definition of Done

- Compiles on target JDK
- Unit and integration tests pass
- Application starts on target runtime image
- CI uses target JDK exclusively (unless dual-run window documented)
- No new compiler deprecation warnings without tracked follow-up
