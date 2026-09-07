# Health Probes and Graceful Shutdown

## Spring Boot Actuator Health Groups

```yaml
management:
  endpoint:
    health:
      probes:
        enabled: true
      group:
        readiness:
          include: readinessState,db
        liveness:
          include: livenessState
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
```

Kubernetes probes:

```yaml
livenessProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 8080
  initialDelaySeconds: 30
  periodSeconds: 10
readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: 8080
  initialDelaySeconds: 10
  periodSeconds: 5
```

## Liveness vs Readiness

- **Liveness**: JVM is up and application context is alive — restart if failing.
- **Readiness**: instance should receive traffic — exclude when DB or required dependency is unavailable.

Do not put slow external checks on liveness unless you want cascading restarts.

## Custom Readiness When Needed

```java
@Component
public class PaymentGatewayHealthIndicator implements HealthIndicator {

    private final PaymentGatewayClient client;

    public PaymentGatewayHealthIndicator(PaymentGatewayClient client) {
        this.client = client;
    }

    @Override
    public Health health() {
        if (client.isReachable()) {
            return Health.up().build();
        }
        return Health.down().withDetail("reason", "payment-gateway-unreachable").build();
    }
}
```

Register only dependencies that should block readiness — not every optional integration.

## Graceful Shutdown

```yaml
server:
  shutdown: graceful
spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s
```

Ensure long-running requests and `@Scheduled` work respect shutdown. Drain executors in `@PreDestroy` or use Spring-managed lifecycle on thread pools.

## Startup Probe for Slow JVM Apps

Cold start on large Spring contexts may need:

```yaml
startupProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 8080
  failureThreshold: 30
  periodSeconds: 10
```
