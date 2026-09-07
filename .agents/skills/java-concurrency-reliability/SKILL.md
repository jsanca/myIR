---
name: java-concurrency-reliability
description: Design and review concurrent Java and Spring Boot code for thread pools, async work, locking, and reliability. Use when fixing race conditions, thread pool exhaustion, deadlocks, lost tasks, unsafe shared state, or async error handling in Java services.
---

# Java Concurrency and Reliability

## Workflow

1. Inspect threading model: virtual threads vs platform threads, `@Async`, `ExecutorService`, scheduled tasks, reactive stack, or blocking JDBC/HTTP in async paths.
2. Identify shared mutable state, lock ordering, pool sizing, queue bounds, and failure propagation.
3. Classify the issue: race condition, pool saturation, unbounded queue, fire-and-forget failure, deadlock, or blocking on the wrong thread.
4. Prefer immutable data, clear ownership, bounded executors, and explicit error handling over ad-hoc synchronization.
5. Make timeouts, cancellation, and backpressure explicit for external calls and background work.
6. Verify with stress tests, thread dump analysis, or deterministic concurrency tests where feasible.

## References

- Read `references/executors-and-virtual-threads.md` for thread pools, virtual threads, and Spring `@Async` configuration.
- Read `references/shared-state-and-locking.md` for safe shared state, locks, and atomic patterns.
- Read `references/reliability-and-timeouts.md` for retries, timeouts, idempotency, and graceful shutdown.

## Reliability Checklist

- Thread pools are bounded and named; rejection policy is intentional.
- Async failures are logged and surfaced — not swallowed.
- Shared mutable state is minimized or guarded with clear invariants.
- Lock ordering is consistent when multiple locks are required.
- Blocking I/O is not run on scarce threads without justification.
- Shutdown hooks or Spring lifecycle stop executors cleanly.
- Timeouts exist on outbound calls that can hang.

## Output

After concurrency work, summarize:

- Failure mode addressed (race, deadlock, pool exhaustion, lost task)
- Threading model before and after
- Files changed and invariants enforced
- Tests or verification performed
- Operational signals to watch (pool metrics, rejected tasks, thread dumps)
