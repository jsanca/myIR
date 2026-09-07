# N+1 Queries and Fetching

## Symptom

One repository call loads a parent list, then Hibernate issues one query per child row when the view or mapper touches a lazy association.

## Fix With Join Fetch (Use Sparingly)

```java
@Query("""
    SELECT DISTINCT o
    FROM Order o
    JOIN FETCH o.lines
    WHERE o.customerId = :customerId
    """)
List<Order> findWithLinesByCustomerId(@Param("customerId") UUID customerId);
```

Use `DISTINCT` when join fetch multiplies parent rows. Prefer this only when you truly need the full graph in one transaction.

## Entity Graph for Controlled Loading

```java
@EntityGraph(attributePaths = {"lines", "lines.product"})
List<Order> findByCustomerId(UUID customerId);
```

Or ad hoc:

```java
@EntityGraph(value = "Order.withLinesAndProduct", type = EntityGraph.EntityGraphType.LOAD)
Optional<Order> findDetailedById(UUID id);
```

## DTO Projection (Often Best for Read APIs)

Avoid loading entities when the API needs a flat shape.

```java
@Query("""
    SELECT new com.example.order.api.OrderSummaryResponse(
        o.id, o.status, o.totalAmount, COUNT(l.id))
    FROM Order o
    JOIN o.lines l
    WHERE o.customerId = :customerId
    GROUP BY o.id, o.status, o.totalAmount
    """)
List<OrderSummaryResponse> summarizeByCustomer(@Param("customerId") UUID customerId);
```

## Spring Data `@Query` With Pagination

```java
@Query(value = """
    SELECT o FROM Order o
    WHERE o.status = :status
    """,
    countQuery = "SELECT COUNT(o) FROM Order o WHERE o.status = :status")
Page<Order> findByStatus(@Param("status") OrderStatus status, Pageable pageable);
```

## Diagnosis Checklist

- Enable `spring.jpa.show-sql` or Hibernate statistics only in local/dev — not production by default.
- Count queries per HTTP request before and after the change.
- Watch for accidental lazy loading in `@Transactional(readOnly = true)` service methods that serialize entities to JSON outside a session (Open Session In View masking vs real fix).
