# GC and Memory Triage

Treat memory issues as leak vs retention vs mis-sized heap before tuning GC algorithms.

## GC Log Enablement (JDK 17+ Unified Logging)

```bash
-Xlog:gc*,safepoint:file=gc.log:time,uptime,level,tags
```

For containers, also verify `-XX:MaxRAMPercentage` or explicit `-Xmx` matches the pod limit.

## Patterns in GC Logs

| Pattern | Likely meaning | Next step |
| --- | --- | --- |
| Frequent young GC, stable old gen | Normal for allocation-heavy workload | Check allocation hotspots |
| Old gen steadily grows, full GC barely reclaims | Leak or retained cache | Heap dump diff |
| Long pause times on G1 | Region sizing, humongous objects | Inspect large arrays/byte[] |
| Metaspace growth after deploy | Classloader leak or hot redeploy | Compare metaspace after redeploy cycles |

## Heap Dump Collection

```bash
jcmd <pid> GC.heap_dump heap-dump.hprof
```

Analyze with Eclipse MAT, VisualVM, or `jhat` only in non-production environments.

## Common Java Memory Fixes (After Evidence)

Cache without bounds:

```java
// Before: unbounded map
private final Map<String, byte[]> cache = new ConcurrentHashMap<>();

// After: bounded cache with eviction
private final Cache<String, byte[]> cache = Caffeine.newBuilder()
    .maximumSize(10_000)
    .expireAfterWrite(Duration.ofMinutes(30))
    .build();
```

Accidental retention via static collections:

```java
// Before
private static final List<RequestContext> RECENT = new ArrayList<>();

// After: ring buffer with max size or remove static retention
```

## Container Memory Checklist

- JVM heap + metaspace + thread stacks + native memory must fit pod limit
- OOMKilled without Java heap OOM often means native/direct memory pressure
- Compare `-Xmx` to Kubernetes `resources.limits.memory`

## Reversible Tuning Order

1. Fix leaks and unbounded caches
2. Right-size heap for container limit
3. Adjust pool sizes with measured backlog
4. Change GC algorithm only with before/after GC log comparison
