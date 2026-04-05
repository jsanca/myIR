package codex.ir.concurrent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Factory for creating {@link VTExecutor} instances.
 * @author jsanca & elo
 */
public final class VTExecutors {

    private VTExecutors() {
        throw new UnsupportedOperationException("Utility class should not be instantiated");
    }

    /**
     * Creates a default executor using virtual threads and a semaphore for concurrency control.
     *
     * @param config the download configuration
     * @return a configured {@link VTExecutor}
     */
    public static VTExecutor createVirtualThreadExecutor(final VTConfig config) {

        Objects.requireNonNull(config, "config must not be null");
        return new VirtualThreadDownloadExecutor(config.maxConcurrentDownloads());
    }

    /**
     * Default implementation using virtual threads + semaphore.
     */
    static final class VirtualThreadDownloadExecutor implements VTExecutor {

        private static final Logger log = LoggerFactory.getLogger(VirtualThreadDownloadExecutor.class);

        private final AtomicBoolean closing = new AtomicBoolean(false);
        private final ExecutorService executor;
        private final Semaphore semaphore;

        VirtualThreadDownloadExecutor(final int maxConcurrentDownloads) {
            if (maxConcurrentDownloads <= 0) {
                throw new IllegalArgumentException("maxConcurrentDownloads must be > 0");
            }
            this.executor = Executors.newVirtualThreadPerTaskExecutor();
            this.semaphore = new Semaphore(maxConcurrentDownloads);
        }

        @Override
        public void execute(final Runnable task) {

            validateTask(task);
            if (this.closing.get()) {
                log.debug("Closing executor");
                return;
            }

            log.debug("Submitting task to executor");
            this.executor.submit(() -> {
                boolean acquired = false;
                try {
                    log.debug("Attempting to acquire permit...");
                    this.semaphore.acquire();
                    acquired = true;
                    log.debug("Permit acquired");
                    log.debug("Executing task");
                    task.run();
                } catch (RejectedExecutionException e) {
                    log.debug("Task rejected because executor is shutting down");
                } catch (InterruptedException e) {
                    log.warn("Task interrupted while waiting for permit", e);
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Task execution interrupted", e);
                } finally {
                    if (acquired) {
                        log.debug("Releasing permit");
                        semaphore.release();
                    }
                }
            });
        }

        @Override
        public void shutdown() {

            this.closing.set(true);
            this.executor.shutdown();
        }
    }
}
