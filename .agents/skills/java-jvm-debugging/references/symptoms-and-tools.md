# Symptoms, Evidence, and Safe Collection Commands

Collect evidence before changing JVM flags, heap sizes, or thread pool settings.

## Symptom to Evidence Map

| Symptom | First evidence | Common non-JVM causes |
| --- | --- | --- |
| Rising heap / OOM | Heap dump, GC logs, allocation rate | Connection leak, unbounded cache |
| Latency spikes | Traces, thread dumps, pool metrics | Slow SQL, downstream timeout |
| High CPU | Thread dump, profiler, hot methods | Regex, serialization loop, bad query |
| Blocked threads | Thread dump, lock analysis | DB pool exhaustion, deadlocks |
| Slow startup | Startup logs, class load metrics | Huge classpath scan, eager beans |
| Frequent restarts | Crash logs, OOMKilled events | Container memory limit too low |

## Thread Dump (Production-Safe When Permitted)

```bash
jcmd <pid> Thread.print > thread-dump-$(date +%Y%m%d-%H%M%S).txt
```

Look for:

- `BLOCKED` threads waiting on the same monitor
- Large numbers of threads in `TIMED_WAITING` on pool queues
- JDBC pool or HTTP client wait stacks

## Heap Histogram (Lightweight)

```bash
jcmd <pid> GC.class_histogram > heap-histogram.txt
```

Use when full heap dump is too heavy. Compare two snapshots taken minutes apart to spot growth.

## Async Profiler (Development or Staging)

```bash
./asprof -d 30 -f profile.html <pid>
```

Prefer staging reproduction when production profiling is restricted.

## Check Pools Together

Thread pool saturation often pairs with connection pool exhaustion:

```yaml
# Example signals to compare on one timeline
- tomcat.threads.busy
- hikaricp.connections.active
- hikaricp.connections.pending
- http.server.requests (p95)
- jdbc.query.duration (p95)
```

## Investigation Output Template

```text
Symptom:
First seen:
Change before issue:
Evidence:
Hypothesis:
Proposed change:
Rollback:
Success signal:
```
