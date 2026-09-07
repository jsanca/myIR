# Reliability, Timeouts, and Shutdown

## Timeouts on Outbound Calls

```java
RestClient restClient = RestClient.builder()
    .requestFactory(new SimpleClientHttpRequestFactory() {{
        setConnectTimeout(Duration.ofSeconds(2));
        setReadTimeout(Duration.ofSeconds(5));
    }})
    .build();
```

For reactive or gRPC clients, apply equivalent deadline propagation.

## Retry With Backoff (Idempotent Operations Only)

```java
@Retryable(
    retryFor = TransientDataAccessException.class,
    maxAttempts = 3,
    backoff = @Backoff(delay = 200, multiplier = 2))
public void publishOutboxEvent(UUID eventId) {
    outboxPublisher.publish(eventId);
}
```

Never blind-retry non-idempotent side effects (payments, email sends) without an idempotency key stored in the database.

## Idempotency Key Pattern

```java
@Transactional
public OrderResponse placeOrder(CreateOrderRequest request, String idempotencyKey) {
    return idempotencyStore.findResult(idempotencyKey)
        .orElseGet(() -> {
            OrderResponse response = createOrder(request);
            idempotencyStore.save(idempotencyKey, response);
            return response;
        });
}
```

## Graceful Executor Shutdown

```java
@PreDestroy
public void shutdown() {
    executor.shutdown();
    try {
        if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
            executor.shutdownNow();
        }
    } catch (InterruptedException ex) {
        executor.shutdownNow();
        Thread.currentThread().interrupt();
    }
}
```

Spring beans with `destroyMethod = "shutdown"` on `ExecutorService` should use a wrapper that awaits in-flight tasks during application stop.

## Operational Signals

- Thread pool: active count, queue size, rejected task count
- JVM: blocked threads, deadlocks in thread dumps
- Application: async error rate, retry counts, duplicate side-effect detection
