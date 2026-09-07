# CI/CD and Configuration

## Typical Pipeline Stages

```text
1. Checkout
2. Unit + integration tests (with Testcontainers if used)
3. Build bootJar / container image
4. Vulnerability scan (image + dependencies)
5. Push immutable tag (git sha or semver)
6. Deploy to staging
7. Smoke test health + critical API path
8. Promote to production with approval
```

## Immutable Tags

Prefer:

```text
registry.example.com/orders-service:2026.06.25-abc1234
```

Avoid `:latest` as the only production tag.

## Environment Configuration

```yaml
# application-prod.yml
spring:
  datasource:
    url: ${SPRING_DATASOURCE_URL}
    username: ${SPRING_DATASOURCE_USERNAME}
    password: ${SPRING_DATASOURCE_PASSWORD}

logging:
  level:
    root: INFO
```

Inject secrets from the platform:

- Kubernetes Secrets / External Secrets Operator
- AWS Secrets Manager / SSM Parameter Store
- Spring Cloud Config with encrypted values

Never commit `.env` files with production credentials.

## Smoke Test After Deploy

```bash
curl -fsS "https://staging.example.com/actuator/health/readiness"
curl -fsS -H "Authorization: Bearer ${SMOKE_TOKEN}" \
  "https://staging.example.com/api/v1/orders?page=0&size=1"
```

Fail the pipeline when readiness is down or the smoke path returns unexpected status codes.

## Database Migrations in Delivery

Decide explicitly:

- **Job/init container** runs Flyway/Liquibase before app rollout
- **App startup** migrates (simpler, tighter coupling)

Document rollback strategy. Schema migrations are not automatically reversible — plan forward fixes.

## Observability Hooks

Expose metrics and traces compatible with your platform:

```yaml
management:
  metrics:
    tags:
      application: ${spring.application.name}
      environment: ${ENVIRONMENT:local}
  tracing:
    sampling:
      probability: ${TRACING_SAMPLE_RATE:0.1}
```

Ensure logs include correlation/trace IDs propagated from ingress.
