package codex.ir.concurrent;

import java.io.Closeable;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Utility that coalesces repeated invocations of a keyed task into a single
 * delayed execution.
 *
 * <p>If {@link #debounce(String, Runnable, long, TimeUnit)} is called multiple
 * times with the same key before the delay expires, only the most recently
 * scheduled task is allowed to run.</p>
 *
 * <p>This is useful for expensive operations such as rebuilding derived
 * metadata, refreshing caches, or reacting to bursty application events.</p>
 *
 * <p>Instances own an internal scheduler and should be closed when no longer
 * needed.</p>
 * @author jsanca & elo
 */
public class Debouncer implements Closeable {

    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final ScheduledExecutorService scheduler;
    private final ConcurrentHashMap<String, ScheduledFuture<?>> delayedMap = new ConcurrentHashMap<>();

    public Debouncer() {
        this(Executors.newSingleThreadScheduledExecutor(new DebouncerThreadFactory()));
    }

    Debouncer(final ScheduledExecutorService scheduler) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler must not be null");
    }

    public void debounce(final String key, final Runnable runnable, final long delay, final TimeUnit unit) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(runnable, "runnable must not be null");
        Objects.requireNonNull(unit, "unit must not be null");

        if (delay < 0) {
            throw new IllegalArgumentException("delay must be >= 0");
        }
        if (closed.get()) {
            throw new IllegalStateException("Debouncer is already closed");
        }

        delayedMap.compute(key, (currentKey, previous) -> {
            if (previous != null) {
                previous.cancel(false);
            }

            return scheduler.schedule(() -> {
                try {
                    runnable.run();
                } finally {
                   cleanUpDoneScheduler(currentKey);
                }
            }, delay, unit);
        });
    }

    private void cleanUpDoneScheduler(final String key) {
        delayedMap.computeIfPresent(key, (k, task) -> task.isDone() ? null : task);
    }


    public void shutdown() {
        if (closed.compareAndSet(false, true)) {
            delayedMap.values().forEach(task -> task.cancel(false));
            delayedMap.clear();
            scheduler.shutdownNow();
        }
    }

    @Override
    public void close() {
        this.shutdown();
    }

    private static final class DebouncerThreadFactory implements ThreadFactory {

        @Override
        public Thread newThread(final Runnable runnable) {
            final Thread thread = new Thread(runnable, "debouncer-thread");
            thread.setDaemon(true);
            return thread;
        }
    }
}