---
name: java-jvm-debugging
description: Diagnose JVM runtime behavior and production issues. Use when investigating Java memory growth, latency spikes, garbage collection, thread dumps, deadlocks, class loading, JVM flags, profiling data, or production performance symptoms.
---

# Java JVM Debugging

## Workflow

1. Start from the symptom: memory, latency, CPU, blocked threads, startup, class loading, or crashes.
2. Collect available evidence before proposing fixes: logs, metrics, traces, heap data, thread dumps, GC logs, and deployment changes.
3. Separate JVM issues from database, network, dependency, and connection pool issues.
4. Prefer reversible, measurable changes.
5. Document the hypothesis, evidence, change, and expected signal.
6. Verify with the same metric or reproduction that showed the problem.

## References

- Read `references/symptoms-and-tools.md` for symptom-to-evidence mapping and safe collection commands.
- Read `references/gc-and-memory.md` for GC log patterns, heap analysis, and memory leak triage.

## Diagnostic Checklist

- Identify what changed before the issue.
- Compare healthy and unhealthy instances when possible.
- Check thread pools and connection pools together.
- Treat JVM flags as production configuration, not code style.
- Avoid speculative tuning without evidence.

## Output

After investigation, summarize:

- Symptom and timeline
- Evidence collected (metrics, dumps, logs)
- Root cause hypothesis and confidence level
- Proposed change and rollback plan
- Metric or command to confirm recovery
