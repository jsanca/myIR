# Spring Boot API Design

Use explicit request and response DTOs rather than exposing persistence entities.

## Controller With DTO Boundaries

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
        OrderResponse response = orderService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public Page<OrderSummaryResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") @Max(100) int size,
            @RequestParam(required = false) OrderStatus status) {
        return orderService.list(page, size, status);
    }
}
```

## Request Validation

```java
public record CreateOrderRequest(
    @NotBlank String sku,
    @Min(1) @Max(1000) int quantity
) {
}
```

## Stable Error Response (RFC 9457 Problem Details)

```java
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(OrderNotFoundException.class)
    public ProblemDetail handleNotFound(OrderNotFoundException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
            HttpStatus.NOT_FOUND,
            "Order not found");
        detail.setTitle("Order Not Found");
        detail.setProperty("orderId", ex.orderId());
        return detail;
    }
}
```

## Pagination Response

```java
public record OrderSummaryResponse(String id, OrderStatus status, Instant createdAt) {
}

// Service returns Page<OrderSummaryResponse>; do not leak Page<Entity> to controllers
```

## API Review Checklist

- Validation annotations and custom validators where needed
- Stable error response shape across endpoints
- Pagination and filtering contracts for list endpoints
- Backward compatibility for existing clients
- Idempotency for retryable write operations (`Idempotency-Key` header or natural keys)

## Avoid Entity Leakage

```java
// Avoid
@GetMapping("/{id}")
public OrderEntity get(@PathVariable UUID id) {
    return repository.findById(id).orElseThrow();
}

// Prefer
@GetMapping("/{id}")
public OrderResponse get(@PathVariable UUID id) {
    return orderService.get(id);
}
```
