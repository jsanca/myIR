---
name: java-21-lts
description: Adopt Java 21 LTS features and migrate toolchains safely. Use when enabling virtual threads, sequenced collections, pattern matching for switch, aligning build/CI/runtime to 21, or verifying a Java 17-to-21 upgrade.
---

# Java 21 LTS

## Workflow

1. Confirm current source, runtime, build tool, CI, and container JDK versions.
2. Upgrade build configuration and runtime images to Java 21 before adopting language features.
3. Run compile and tests on 21 with no `--enable-preview` unless explicitly requested.
4. Apply Java 21 features where they improve clarity or throughput without changing behavior unintentionally.
5. Adopt virtual threads only for blocking I/O workloads after baseline correctness passes on platform threads.
6. Verify toolchain alignment across local dev, CI, and deployment.

## References

- Read `references/language-features.md` for virtual threads, sequenced collections, and pattern matching for switch.
- Read `references/migration-to-21.md` for build, CI, dependency, and verification steps.

## Adoption Checklist

- `java -version` and compiler release/toolchain target 21 everywhere.
- Dependencies and annotation processors support Java 21 bytecode.
- Sequenced collection APIs replace ad-hoc first/last/reverse helpers where applicable.
- Switch expressions use pattern matching when branches are clearer than `instanceof` chains.
- Virtual threads are scoped to blocking I/O; pool sizing and pinning are measured, not assumed.
- Preview features (for example string templates) are not enabled by default.

## Output

After Java 21 work, summarize:

- Versions changed (source, runtime, CI, containers)
- Features adopted and why
- Files or configuration updated
- Tests run and results
- Remaining blockers or follow-ups (preview features, framework upgrades)
