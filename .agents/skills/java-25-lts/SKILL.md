---
name: java-25-lts
description: Adopt Java 25 LTS features and verify toolchain alignment. Use when upgrading from Java 21 to 25, applying finalized language or library features, aligning Gradle/Maven/CI/runtime to 25, or validating migration without preview features.
---

# Java 25 LTS

## Workflow

1. Confirm current source, runtime, build tool, CI, and container JDK versions (often Java 21 LTS today).
2. Upgrade build configuration and runtime images to Java 25 before changing source.
3. Compile and test on 25 with no `--enable-preview` unless explicitly requested and approved.
4. Apply **finalized** Java 25 features where they improve clarity or operational behavior.
5. Treat preview and incubator features (structured concurrency, primitive patterns, vector API) as out of scope unless the repo already enables them.
6. Verify alignment across local dev, CI, packaging, and deployment runtime.

## References

- Read `references/language-and-library-features.md` for finalized Java 25 language and library features with samples.
- Read `references/migration-and-verification.md` for toolchain alignment, compatibility checks, and verification commands.

## Adoption Checklist

- Compiler release/toolchain and `java -version` report 25 everywhere.
- Framework BOM, plugins, and bytecode tools declare Java 25 support.
- Flexible constructor bodies and module import declarations used only where they simplify real code.
- Scoped values considered for immutable request context instead of `ThreadLocal` when appropriate.
- Preview/incubator JEPs are not enabled by default.
- Performance-sensitive paths re-benchmarked after upgrade.

## Output

After Java 25 work, summarize:

- Versions changed (source, runtime, CI, containers)
- Finalized features adopted and why
- Preview features deferred and why
- Files or configuration updated
- Tests run and results
- Remaining blockers or follow-ups
