package codex.apps.siteexporter;

import java.io.IOException;

/**
 * Produces a publication artifact from a mirror source.
 *
 * <p>Each implementation encapsulates the internals of one output format
 * (PDF, Markdown, …). The caller supplies a {@link PublicationSource} and
 * {@link PublicationExportOptions}; the driver decides what tools to use.</p>
 *
 * <p>Implementations are obtained via {@link PublicationDrivers#forFormat}.</p>
 */
public interface PublicationDriver {

    /**
     * Returns {@code true} if asset download and HTML link-rewriting must run
     * before {@link #publish} is called.
     *
     * <p>PDF rendering depends on resolved CSS and images; text-extraction
     * formats (Markdown) do not.</p>
     */
    boolean requiresAssetProcessing();

    /**
     * Produces the publication artifact.
     *
     * @param source  content directory and mirror manifest; must not be {@code null}
     * @param options target format and output path; must not be {@code null}
     * @return the produced artifact; never {@code null}
     * @throws IOException if rendering, writing, or I/O fails
     */
    PublicationArtifact publish(PublicationSource source, PublicationExportOptions options)
            throws IOException;
}
