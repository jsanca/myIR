package codex.apps.siteexporter;

import java.util.concurrent.ExecutorService;

/**
 * Controls the thread model and concurrency limit for independent per-page work
 * (primarily PDF rendering) inside a {@link PublicationPipeline}.
 *
 * <p>The default implementation is {@link VirtualThreadPipelineExecutionPolicy},
 * which uses one virtual thread per task — the same thread model used by
 * {@link codex.ir.concurrent.VTExecutors} for asset downloads.</p>
 *
 * <p>Bounded parallelism is enforced by {@link PublicationPipeline} using a
 * {@link java.util.concurrent.Semaphore} constructed from {@link #parallelism()},
 * following the pattern established by
 * {@link codex.ir.concurrent.VTExecutors.VirtualThreadDownloadExecutor}.</p>
 */
public interface PipelineExecutionPolicy {

    /**
     * Maximum number of render tasks that may run concurrently.
     * {@link Integer#MAX_VALUE} means unbounded.
     */
    int parallelism();

    /**
     * Creates a new {@link ExecutorService} for this policy.
     * The caller is responsible for shutdown (typically via try-with-resources).
     */
    ExecutorService newExecutor();
}
