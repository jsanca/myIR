# Java 21 Language and Library Features

Use finalized Java 21 features first. Do not enable preview features unless the repository already opts in with `--enable-preview` and team approval.

## Virtual Threads (JEP 444)

Use for high fan-out **blocking** I/O. Do not expect throughput gains on CPU-bound work.

```java
public final class ReportFetcher {

    private final HttpClient httpClient = HttpClient.newHttpClient();

    public List<String> fetchAll(List<URI> endpoints) throws InterruptedException {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<String>> futures = endpoints.stream()
                .map(uri -> executor.submit(() -> fetchBody(uri)))
                .toList();

            List<String> bodies = new ArrayList<>(futures.size());
            for (Future<String> future : futures) {
                bodies.add(future.get());
            }
            return List.copyOf(bodies);
        }
    }

    private String fetchBody(URI uri) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri).GET().build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new IOException("HTTP " + response.statusCode() + " for " + uri);
        }
        return response.body();
    }
}
```

Spring Boot 3.2+ can route servlet work to virtual threads when configured explicitly. Measure pinning (for example synchronized blocks in legacy libraries) before broad rollout.

## Sequenced Collections (JEP 431)

`SequencedCollection`, `SequencedSet`, and `SequencedMap` add consistent first/last and reverse views.

```java
public final class QueueHistory {

    private final Deque<String> events = new ArrayDeque<>();

    public void record(String event) {
        events.addLast(event);
    }

    public Optional<String> latestEvent() {
        return events.isEmpty() ? Optional.empty() : Optional.of(events.getLast());
    }

    public List<String> newestFirst() {
        return events.reversed().stream().toList();
    }

    public void trimOldest(int keepCount) {
        while (events.size() > keepCount) {
            events.removeFirst();
        }
    }
}
```

Prefer `getFirst()`, `getLast()`, and `reversed()` over manual index math or copying lists just to read ends.

## Pattern Matching for switch (JEP 441)

Use when type-specific handling is clearer than chained `instanceof` checks.

```java
public sealed interface ShipmentEvent permits ShipmentCreated, ShipmentDelayed, ShipmentDelivered {
}

public record ShipmentCreated(String trackingId, Instant createdAt) implements ShipmentEvent {
}

public record ShipmentDelayed(String trackingId, Duration delay) implements ShipmentEvent {
}

public record ShipmentDelivered(String trackingId, Instant deliveredAt) implements ShipmentEvent {
}

public String describe(ShipmentEvent event) {
    return switch (event) {
        case ShipmentCreated created ->
            "Created " + created.trackingId() + " at " + created.createdAt();
        case ShipmentDelayed delayed ->
            "Delayed " + delayed.trackingId() + " by " + delayed.delay().toMinutes() + "m";
        case ShipmentDelivered delivered ->
            "Delivered " + delivered.trackingId() + " at " + delivered.deliveredAt();
    };
}
```

Combine with sealed types for exhaustiveness. Keep `default` only when the hierarchy is intentionally open.

## Record Patterns (JEP 440)

Deconstruct records directly in `instanceof` and `switch`.

```java
public boolean isHighValueOrder(Object payload) {
    return payload instanceof OrderLine(String sku, int quantity, MoneyAmount(var amount, _))
        && quantity >= 10
        && amount.compareTo(BigDecimal.valueOf(1_000)) >= 0;
}
```

Use when nested record shape is stable and the deconstruction improves readability.

## When Not to Adopt Yet

- **String templates (preview)** — wait for final release and team policy.
- **Virtual threads everywhere** — keep platform thread pools for CPU work and legacy pinning hotspots.
- **Pattern matching churn** — do not rewrite working code solely for syntax; adopt where branches are error-prone today.
