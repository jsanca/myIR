# Connection Pool and Query Tuning

## HikariCP Baseline (Spring Boot)

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
      pool-name: app-primary
```

Rules of thumb:

- Pool size is not "more is better" — align with DB `max_connections` and instance count.
- If threads block on `getConnection()`, investigate slow queries and long transactions before raising pool size.
- Set `max-lifetime` below database or proxy idle cutoff to avoid stale connections.

## Detect Slow Queries

Look for:

- Full table scans on large tables
- Functions on indexed columns in `WHERE` clauses (`WHERE LOWER(email) = ?` without functional index)
- `SELECT *` on wide rows when a projection suffices
- Missing composite indexes for common `(filter, sort)` patterns

```sql
-- Example supporting index for list endpoint
CREATE INDEX idx_order_customer_status_created
    ON orders (customer_id, status, created_at DESC);
```

## Keyset Pagination for Large Lists

Offset pagination degrades on deep pages.

```java
@Query("""
    SELECT o FROM Order o
    WHERE o.customerId = :customerId
      AND (o.createdAt < :cursorTime
           OR (o.createdAt = :cursorTime AND o.id < :cursorId))
    ORDER BY o.createdAt DESC, o.id DESC
    """)
List<Order> findNextPage(
    @Param("customerId") UUID customerId,
    @Param("cursorTime") Instant cursorTime,
    @Param("cursorId") UUID cursorId,
    Pageable pageable);
```

## JDBC Client for Reporting Queries

Use Spring `JdbcClient` or jOOQ for read-heavy SQL that does not belong on entities.

```java
public List<DailyTotalRow> dailyTotals(LocalDate from, LocalDate to) {
    return jdbcClient.sql("""
            SELECT day, SUM(amount) AS total
            FROM payment
            WHERE day BETWEEN :from AND :to
            GROUP BY day
            ORDER BY day
            """)
        .param("from", from)
        .param("to", to)
        .query(DailyTotalRow.class)
        .list();
}
```
