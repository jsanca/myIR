---
name: java-cloud-native-delivery
description: Deliver Java and Spring Boot services to containers and cloud platforms with health checks, configuration, observability hooks, and CI/CD. Use when dockerizing JVM apps, configuring liveness/readiness probes, tuning container memory, setting up deployment pipelines, or preparing Java services for Kubernetes and cloud runtimes.
---

# Java Cloud Native Delivery

## Workflow

1. Inspect build tool, Java version, Spring Boot version, existing Dockerfile, compose files, CI pipeline, and deployment target (Kubernetes, ECS, Cloud Run, VM).
2. Identify gaps: container image size, JVM flags in containers, config via env vars, health endpoints, graceful shutdown, metrics/traces, and secret injection.
3. Prefer twelve-factor configuration: environment-specific settings through env vars or config service, not baked into images.
4. Configure liveness vs readiness separately — readiness should reflect dependency availability the platform should respect.
5. Set container-aware JVM defaults and validate memory limits against heap, metaspace, threads, and native overhead.
6. Verify with local container run, health probe checks, CI build/push, and smoke test against the deployed artifact.

## References

- Read `references/containers-and-jvm.md` for Dockerfiles, layered JARs, JVM container flags, and local run patterns.
- Read `references/health-probes-and-shutdown.md` for Actuator health groups, probes, and graceful shutdown.
- Read `references/cicd-and-config.md` for CI/CD stages, env config, secrets, and deployment smoke checks.

## Delivery Checklist

- Image runs as non-root where possible.
- Config and secrets come from environment or secret store — not the image layer.
- Liveness and readiness probes target meaningful endpoints.
- JVM respects container memory limits (`MaxRAMPercentage` or explicit caps).
- Application stops gracefully on SIGTERM within platform timeout.
- Metrics and structured logs are available for the runtime environment.
- CI builds, tests, scans, and produces an immutable image tag or artifact.

## Output

After delivery work, summarize:

- Target runtime and deployment change
- Files added or updated (Dockerfile, CI, K8s manifest, compose)
- Health, config, and JVM decisions
- Commands to build, run locally in container, and deploy
- Operational follow-ups (ingress, autoscaling, DB migrations, secrets rotation)
