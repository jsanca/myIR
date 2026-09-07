---
name: java-spring-boot-41
description: Upgrade to or adopt Spring Boot 4.1 features on a Boot 4.0 baseline. Use when moving from 4.0.x to 4.1.x, adding Spring gRPC, new Jackson configuration properties, HTTP client SSRF protections, OpenTelemetry updates, or Log4j file rotation.
---

# Java Spring Boot 4.1

Spring Boot **4.1** is a **minor** release on the Boot 4 line (Spring Framework **7.0.8+**, Spring Security **7.1**). Require **Boot 4.0** on a green test suite before upgrading.

## Workflow

1. Confirm the project already runs **Spring Boot 4.0.x** and Java **17+** — if not, use `java-spring-boot-40` first.
2. Read `references/upgrade-from-40.md` for patch upgrade steps and dependency alignment.
3. Bump Boot to **4.1.x** (latest patch), sync Spring Cloud or third-party BOMs if present, and run compile plus tests.
4. Read `references/new-features.md` when the task involves gRPC, Jackson customization properties, HTTP client hardening, observability, or Log4j rotation.
5. Adopt 4.1 features only when they match the requested work — do not rewrite working 4.0 configuration unnecessarily.
6. Re-verify Security, JSON serialization, and integration tests after the bump.

## References

- Read `references/upgrade-from-40.md` for 4.0 → 4.1 upgrade and verification.
- Read `references/new-features.md` for gRPC, Jackson, HTTP client SSRF mitigation, observability, and logging highlights.

## Adoption Checklist

- Boot version is **4.1.x** on Framework 7.0.8+ and Security 7.1+ per project BOM.
- Spring Cloud / platform BOMs compatible with Boot 4.1 (if used).
- gRPC additions include tests and channel/security configuration — not only dependencies.
- Outbound HTTP clients reviewed for SSRF mitigation when using new filter APIs.
- Observability exporters still emit traces and metrics after OpenTelemetry updates.
- Log rotation settings validated in the target deployment environment.

## Output

After Boot 4.1 work, summarize:

- Versions changed (Boot, Framework, Security, related BOMs)
- New 4.1 capabilities adopted and why
- Tests run and results
- Configuration or security follow-ups
