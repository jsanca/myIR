package codex.ir.ingestion.crawler.product;

/**
 * Writes a {@link ProductDiscoveryReport} to an output destination.
 *
 * <p>Implementations may target console, file, or any other sink.
 * Implementations must not modify the report.</p>
 */
@FunctionalInterface
public interface DiscoveryReportWriter {

    /**
     * Writes the given report to the configured destination.
     *
     * @param report the product discovery report to write; must not be {@code null}
     */
    void write(ProductDiscoveryReport report);
}
