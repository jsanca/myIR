---
name: java-spring-boot-service
description: Build, review, refactor, and test Spring Boot backend services. Use when working on Spring Boot REST APIs, dependency injection, validation, configuration, profiles, transactions, observability, service tests, or production service behavior.
---

# Java Spring Boot Service

## Workflow

1. Inspect the Java version, Spring Boot version, build tool, package layout, tests, and configuration style.
2. Route version-specific work to the matching skill when the BOM target is clear: **3.5** → `java-spring-boot-35`, **4.0 migration** → `java-spring-boot-40`, **4.1** → `java-spring-boot-41`; use this skill for general service implementation on any supported Boot 3.x/4.x line.
3. Identify whether the task is implementation, review, refactor, test coverage, migration, or production behavior.
4. Follow existing controller, service, repository, DTO, validation, and configuration patterns.
5. Keep API boundaries explicit: request DTOs, response DTOs, validation, error responses, pagination, and compatibility.
6. Check transaction boundaries, persistence access, security assumptions, and observability signals.
7. Verify with targeted unit or slice tests first, then broader Spring Boot tests when behavior crosses layers.

## References

- Read `references/api-design.md` for controller, DTO, pagination, filtering, and error response work.
- Read `references/testing.md` for Spring Boot test strategy and slice test examples.
- Read `references/vertical-slice.md` for end-to-end feature implementation patterns.

## Output

After changes, summarize:

- Files changed
- Behavior added or fixed
- Tests added and why each test type was chosen
- Commands to run (`./gradlew test`, `./mvn test`, or narrower targets)
- Risks, migration notes, or API compatibility concerns
