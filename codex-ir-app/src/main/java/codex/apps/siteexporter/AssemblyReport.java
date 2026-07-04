package codex.apps.siteexporter;

import java.util.List;
import java.util.Objects;

/**
 * Summary of a {@link PublicationPipeline} run: how many pages were attempted,
 * rendered successfully, and which failed.
 *
 * <p>{@code pagesAttempted} is the count returned by the ordering strategy.
 * {@code pagesRendered} is the count that produced a PDF without error.
 * {@code pagesFailed} equals {@code pagesAttempted - pagesRendered}.</p>
 */
public record AssemblyReport(
        int pagesAttempted,
        int pagesRendered,
        int pagesFailed,
        long outputSizeBytes,
        List<RenderFailure> renderFailures) {

    public AssemblyReport {
        Objects.requireNonNull(renderFailures, "renderFailures");
        if (pagesAttempted < 0) throw new IllegalArgumentException("pagesAttempted must be >= 0");
        if (pagesRendered < 0) throw new IllegalArgumentException("pagesRendered must be >= 0");
        if (pagesFailed < 0)   throw new IllegalArgumentException("pagesFailed must be >= 0");
        if (outputSizeBytes < 0) throw new IllegalArgumentException("outputSizeBytes must be >= 0");
        renderFailures = List.copyOf(renderFailures);
    }

    /**
     * Records a page that could not be rendered.
     *
     * @param pageUrl URL of the page that failed
     * @param reason  error message or exception description
     */
    public record RenderFailure(String pageUrl, String reason) {
        public RenderFailure {
            Objects.requireNonNull(pageUrl, "pageUrl");
            Objects.requireNonNull(reason, "reason");
        }
    }
}
