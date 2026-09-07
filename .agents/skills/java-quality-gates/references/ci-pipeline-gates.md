# Java CI Pipeline Gates

Always match the project's build tool and existing CI — do not paste a greenfield pipeline over a Maven repo.

## Canonical Commands

| Build tool | Typical full gate | Fast PR subset |
| --- | --- | --- |
| Gradle | `./gradlew check` | `./gradlew test checkstyleMain spotbugsMain` |
| Maven | `./mvn verify` | `./mvn test` + bound quality plugins |

`check` (Gradle) usually includes tests, Checkstyle, and other lifecycle tasks wired by plugins.

## Recommended PR Pipeline

```yaml
# Example — adapt job names and JDK to project
jobs:
  quality:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "21"
          cache: gradle
      - run: ./gradlew check --no-daemon
```

## Gate Layers (block merge when)

| Layer | Tool examples | Block PR? |
| --- | --- | --- |
| Compile + unit tests | JUnit 5, Surefire, `./gradlew test` | Yes |
| Static analysis | Checkstyle, SpotBugs, PMD, Error Prone | Yes (when adopted) |
| Formatting | Spotless, Palantir Java Format | Yes |
| Coverage floor | JaCoCo report + minimum on packages | Yes on critical modules |
| Integration tests | Testcontainers, `@SpringBootTest` subset | Yes if stable; optional while flaking |

## Main Branch vs PR

- **PR:** fast feedback — unit tests + static analysis + format check.
- **Main/nightly:** full integration suite, coverage report upload, optional SonarQube.

Do not run 30-minute suites on every PR unless infra supports parallel shards reliably.

## Reproducing CI Locally

```bash
jenv shell 21   # match project major
./gradlew check
# or
./mvn -q verify
```

Document the exact JDK major in README or `CONTRIBUTING.md`.

## Advisory vs Blocking

| Advisory (warn first) | Blocking (fail build) |
| --- | --- |
| New rule on legacy module with baseline file | Test failures |
| Sonar "code smell" without team agreement | Checkstyle errors on touched packages |
| Coverage dip on generated code | Enforcer dependency convergence failures |

When introducing a new gate on legacy code, use a **ratchet**: fail on new violations only, then tighten.

## Related Skills

- `testing-strategy` — which tests belong in PR vs main
- `java-cloud-native-delivery` — container smoke after build passes
