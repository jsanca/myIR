# Executors and Virtual Threads

## Named Bounded Thread Pool

```java
@Configuration
public class ExecutorConfig {

    @Bean(destroyMethod = "shutdown")
    ExecutorService notificationExecutor() {
        ThreadFactory factory = Thread.ofPlatform()
            .name("notify-", 0)
            .uncaughtExceptionHandler((thread, ex) ->
                LoggerFactory.getLogger(ExecutorConfig.class)
                    .error("Uncaught in {}", thread.getName(), ex))
            .factory();

        return new ThreadPoolExecutor(
            4,
            16,
            60,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(500),
            factory,
            new ThreadPoolExecutor.CallerRunsPolicy());
    }
}
```

Document why `CallerRunsPolicy` (or `AbortPolicy`) was chosen. Unbounded queues hide overload until the JVM runs out of memory.

## Spring `@Async` With Explicit Executor

```java
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("async-");
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(200);
        executor.initialize();
        return executor;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) ->
            LoggerFactory.getLogger(method.getDeclaringClass())
                .error("Async failure in {} with params {}", method.getName(), params, ex);
    }
}
```

## Virtual Threads (Java 21+)

Use for high fan-out blocking I/O when the codebase is mostly blocking APIs.

```java
@Bean(destroyMethod = "close")
ExecutorService virtualThreadExecutor() {
    return Executors.newVirtualThreadPerTaskExecutor();
}
```

Do not move CPU-bound work to virtual threads expecting magic throughput. Pinning issues (synchronized blocks inside JDK I/O) still matter — measure before broad adoption.

## Fire-and-Forget Is a Smell

```java
// Risky — exceptions disappear
CompletableFuture.runAsync(() -> emailSender.send(message));

// Better — handle failure
CompletableFuture.runAsync(() -> emailSender.send(message), notificationExecutor)
    .exceptionally(ex -> {
        log.warn("Notification failed orderId={}", orderId, ex);
        deadLetterQueue.enqueue(orderId);
        return null;
    });
```
