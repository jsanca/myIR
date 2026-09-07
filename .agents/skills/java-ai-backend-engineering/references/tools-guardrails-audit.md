# Tools, Guardrails, and Audit

## Tool Executor Bound to Session

```java
@Component
public class OrderTools {

    private final OrderQueryService orderQueryService;

    public OrderTools(OrderQueryService orderQueryService) {
        this.orderQueryService = orderQueryService;
    }

    @Tool("Search orders for the authenticated customer")
    public List<OrderSummary> searchOrders(
            @P("Optional status filter") OrderStatus status,
            ToolContext context) {
        UUID customerId = context.requireCustomerId();
        return orderQueryService.search(customerId, status);
    }
}
```

Register tools in a dedicated config module; validate tool count and descriptions at startup in tests.

## Input Guardrail (Scope Check)

```java
@Component
@Order(10)
public class ChatScopeGuard implements ChatPipelineStage {

    @Override
    public ChatStageResult process(ChatPipelineContext ctx) {
        if (ctx.request().message().length() > 4000) {
            return ChatStageResult.blocked("Message too long");
        }
        if (ctx.guardrailProperties().isOutOfScope(ctx.request().message())) {
            return ChatStageResult.refusal("Question outside supported catalog scope");
        }
        return ChatStageResult.continuePipeline();
    }
}
```

## Cost / Token Guardrail

```java
if (ctx.sessionTokenUsage().total() > properties.maxSessionTokens()) {
    return ChatStageResult.refusal("Session limit reached");
}
```

Combine with per-tenant rate limiting at the API gateway or controller.

## Audit Event (Safe Fields)

```java
public record ChatAuditEvent(
    UUID conversationId,
    UUID userId,
    Duration latency,
    TokenUsage tokenUsage,
    List<String> sourceIds
) {}
```

Avoid storing full prompts/responses containing PII in hot logs unless retention policy and redaction are defined.

## Testing With Mocks

```java
@Test
void chat_uses_retrieval_sources_in_pipeline() {
    when(retrievalPort.retrieve(any())).thenReturn(List.of(candidate("doc-1")));
    when(llmPort.complete(any())).thenReturn(new LlmCompletion("answer", tokenUsage(10, 5)));

    ChatResponse response = chatService.chat(request, user);

    assertThat(response.answer()).contains("answer");
    verify(retrievalPort).retrieve(argThat(q -> q.tenantId().equals("tenant-a")));
}
```

Use WireMock or test doubles for HTTP LLM clients; do not require live API keys in CI.
