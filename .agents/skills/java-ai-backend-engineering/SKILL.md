---
name: java-ai-backend-engineering
description: Build AI features in Java and Spring Boot backends including LLM clients, RAG retrieval ports, tool execution, guardrails, and audit. Use when implementing chat APIs, embedding/indexing jobs, agent tools, streaming responses, or integrating LangChain4j-style stacks in JVM services.
---

# Java AI Backend Engineering

## Workflow

1. Inspect stack: Java version, Spring Boot generation, existing LLM/RAG libraries, config, and test patterns.
2. Separate concerns: **chat orchestration**, **retrieval**, **tool execution**, **guardrails**, **audit** — use ports/adapters, not one god service.
3. Keep domain modules from importing chat-specific types; expose SPI ports at boundaries (`CatalogRetrievalPort`, `ToolExecutor`, etc.).
4. Implement retrieval with explicit filters, timeouts, and token budgets; never pass unbounded context to the model.
5. Execute tools server-side with authorization bound to the authenticated session — not model-supplied IDs.
6. Log audit events (latency, tokens, source IDs) without sensitive corpus or user PII unless policy allows.
7. Verify with unit tests for orchestration, integration tests for retrieval/tools, and contract tests for chat APIs.

## References

- Read `references/spring-llm-and-rag-ports.md` for Spring service layout, retrieval ports, and streaming patterns.
- Read `references/tools-guardrails-audit.md` for tool wiring, guardrails, cost limits, and audit logging.

## Engineering Checklist

- API keys and provider secrets from environment/secret manager.
- Retrieval enforces tenant/ACL filters before top-k.
- Chat endpoints have rate limits and request size caps.
- Timeouts on LLM and tool calls; structured errors to clients.
- Idempotency for side-effecting tool paths where needed.
- Feature flags for model/provider switches.
- Tests mock LLM/tool ports — CI does not call production providers by default.

## Output

After implementation, summarize:

- Components added (controller, orchestrator, ports, adapters)
- Data flows for chat and retrieval
- Config keys and secrets required (names only — no values)
- Tests added and how LLM/tool calls are mocked
- Operational metrics to expose
