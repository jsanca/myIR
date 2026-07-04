package codex.apps.siteexporter;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A {@link PipelineExecutionPolicy} backed by virtual threads and a
 * {@link java.util.concurrent.Semaphore} for bounded concurrency.
 *
 * <p>Mirrors the design of {@link codex.ir.concurrent.VTExecutors.VirtualThreadDownloadExecutor}:
 * one virtual thread is created per task; a semaphore limits how many may execute
 * simultaneously. Unlike {@code VTExecutor}, this policy exposes an
 * {@link ExecutorService} so that callers can collect {@code Future} results in
 * index order, which is required for preserving page assembly order.</p>
 *
 * <p>Use {@link #unbounded()} for maximum throughput (one virtual thread per page,
 * all pages render simultaneously). Use {@link #bounded(int)} to cap memory and
 * CPU pressure when mirroring large sites.</p>
 */
public final class VirtualThreadPipelineExecutionPolicy implements PipelineExecutionPolicy {

    /** Sentinel value indicating no concurrency limit. */
    public static final int UNBOUNDED = Integer.MAX_VALUE;

    private final int parallelism;

    private VirtualThreadPipelineExecutionPolicy(final int parallelism) {
        this.parallelism = parallelism;
    }

    /**
     * Returns a policy with no concurrency limit: all pages render simultaneously.
     */
    public static VirtualThreadPipelineExecutionPolicy unbounded() {
        return new VirtualThreadPipelineExecutionPolicy(UNBOUNDED);
    }

    /**
     * Returns a policy that allows at most {@code parallelism} concurrent render tasks.
     *
     * @param parallelism maximum concurrent renders; must be &gt;= 1
     * @throws IllegalArgumentException if {@code parallelism} is less than 1
     */
    public static VirtualThreadPipelineExecutionPolicy bounded(final int parallelism) {
        if (parallelism < 1) {
            throw new IllegalArgumentException("parallelism must be >= 1, got: " + parallelism);
        }
        return new VirtualThreadPipelineExecutionPolicy(parallelism);
    }

    @Override
    public int parallelism() {
        return parallelism;
    }

    @Override
    public ExecutorService newExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
