---
name: java-persistence-performance
description: Diagnose and improve Java persistence performance with JPA, Hibernate, JDBC, transactions, and connection pools. Use when fixing slow queries, N+1 loading, missing indexes, transaction boundaries, fetch plans, batch writes, or database connection pool exhaustion.
---

# Java Persistence Performance

## Workflow

1. Inspect the persistence stack: JPA/Hibernate version, Spring Data usage, JDBC clients, migration tool, datasource config, and pool settings.
2. Reproduce or measure the issue with SQL logging, metrics, slow-query logs, or profiler output — do not guess from code alone.
3. Classify the problem: N+1 queries, over-fetching, missing index, long transaction, lock contention, pool starvation, or chatty write pattern.
4. Prefer the smallest fix: fetch join, `@EntityGraph`, DTO projection, query rewrite, index, batch size, or transaction scope change.
5. Avoid loading full entity graphs when a projection or paginated query is enough.
6. Verify with before/after query counts, integration tests, or load test on the affected path.

## References

- Read `references/n-plus-one-and-fetching.md` for N+1 diagnosis, fetch joins, entity graphs, and projections.
- Read `references/transactions-and-locking.md` for transaction boundaries, read-only queries, optimistic locking, and batch writes.
- Read `references/pool-and-query-tuning.md` for datasource pool settings, SQL tuning signals, and pagination patterns.

## Performance Checklist

- Query count per request is understood and acceptable.
- Lazy associations are not accessed outside an intentional fetch plan.
- Writes use batching where volume warrants it.
- Transactions are no longer than necessary.
- Pagination is applied at the database, not in memory.
- Indexes support filter and join columns used in hot queries.
- Connection pool size matches workload and downstream DB limits.

## Output

After persistence work, summarize:

- Symptom and root cause (with evidence: query count, SQL, metric)
- Change made and why it is the smallest safe fix
- Files and queries affected
- Verification performed (tests, logs, load check)
- Residual risks or queries still needing DBA review
