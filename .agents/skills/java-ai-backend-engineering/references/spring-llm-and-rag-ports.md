# Spring LLM and RAG Ports

## Layering (Ports and Adapters)

```text
chat.api.ChatController
  -> chat.service.ChatService
  -> chat.pipeline.* (ordered stages)
  -> chat.spi.CatalogRetrievalPort  (implemented by catalog.rag adapter)
  -> chat.spi.LlmChatPort            (OpenRouter / LangChain4j adapter)
```

Domain catalog code must not import `chat.*`.

## Retrieval Port Example

```java
public interface CatalogRetrievalPort {

    List<RetrievalCandidate> retrieve(RetrievalQuery query);
}

public record RetrievalQuery(
    String text,
    String tenantId,
    int maxResults,
    Duration timeout
) {}
```

```java
@Service
public class RetrievalServiceAdapter implements CatalogRetrievalPort {

    private final RetrievalService retrievalService;

    public RetrievalServiceAdapter(RetrievalService retrievalService) {
        this.retrievalService = retrievalService;
    }

    @Override
    public List<RetrievalCandidate> retrieve(RetrievalQuery query) {
        return retrievalService.search(
            query.text(),
            query.tenantId(),
            query.maxResults(),
            query.timeout());
    }
}
```

## Chat Service With Budget

```java
@Service
public class ChatService {

    private final ChatPipeline pipeline;
    private final ChatAuditLogger auditLogger;

    public ChatResponse chat(ChatRequest request, AuthenticatedUser user) {
        Instant start = Instant.now();
        ChatResponse response = pipeline.run(request, user);
        auditLogger.record(new ChatAuditEvent(
            request.conversationId(),
            user.id(),
            Duration.between(start, Instant.now()),
            response.tokenUsage(),
            response.sourceIds()));
        return response;
    }
}
```

## Streaming (SSE) Controller Sketch

```java
@PostMapping(value = "/api/v1/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<ServerSentEvent<String>> stream(@Valid @RequestBody ChatRequest request,
                                            @AuthenticationPrincipal UserPrincipal user) {
    return chatStreamingService.stream(request, user)
        .map(chunk -> ServerSentEvent.builder(chunk).build())
        .timeout(Duration.ofSeconds(60));
}
```

Apply backpressure and timeout; close stream on client disconnect.

## Configuration

```yaml
syj:
  ai:
    provider: openrouter
    model: ${AI_CHAT_MODEL}
    api-key: ${AI_API_KEY}
    max-prompt-tokens: 8000
    retrieval-top-k: 5
    request-timeout: 30s
```

Use `@ConfigurationProperties` with validation — never hardcode keys.
