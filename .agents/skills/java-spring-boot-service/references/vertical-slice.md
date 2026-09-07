# Vertical Slice Feature Implementation

Implement the smallest end-to-end slice: DTO, controller, service, persistence (if needed), validation, migration, tests.

## 1. Request and Response DTOs

```java
public record CreateOrderRequest(
    @NotBlank String sku,
    @Min(1) int quantity
) {
}

public record OrderResponse(String id, OrderStatus status) {
}
```

## 2. Service With Transaction Boundary

```java
@Service
public class OrderService {

    private final OrderRepository repository;

    public OrderService(OrderRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public OrderResponse create(CreateOrderRequest request) {
        OrderEntity entity = new OrderEntity(UUID.randomUUID().toString(), request.sku(), request.quantity());
        repository.save(entity);
        return new OrderResponse(entity.getExternalId(), entity.getStatus());
    }
}
```

## 3. Controller

```java
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> create(@Valid @RequestBody CreateOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.create(request));
    }
}
```

## 4. Schema Migration (When Needed)

```sql
-- V2__create_orders.sql
CREATE TABLE orders (
    id BIGSERIAL PRIMARY KEY,
    external_id VARCHAR(64) NOT NULL UNIQUE,
    sku VARCHAR(64) NOT NULL,
    quantity INT NOT NULL,
    status VARCHAR(32) NOT NULL
);
```

## 5. Tests for the Slice

- Unit test for `OrderService` business rules
- `@WebMvcTest` for controller validation and HTTP contract
- `@DataJpaTest` or Testcontainers if query or constraint behavior matters

## Boot Version Notes

- Spring Boot 3.x: Jakarta (`jakarta.*`) namespaces
- Spring Boot 4.x: Spring Framework 7, verify Jackson and security config against project BOM
- Match imports and test annotations to the project's existing modules before adding new dependencies

## Completion Checklist

- DTOs at API boundary, entities not exposed
- Validation on request models
- Transaction scope only where persistence changes occur
- Migration added when schema changes
- Tests at the narrowest useful level
- Run `./gradlew test` or `mvn test` for the affected package
