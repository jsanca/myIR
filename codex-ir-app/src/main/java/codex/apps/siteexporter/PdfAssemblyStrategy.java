package codex.apps.siteexporter;

import java.io.IOException;
import java.util.List;

/**
 * Assembles per-page PDF byte arrays into a single combined PDF.
 *
 * <p>Implementations are provided in Phase 10 (e.g. a manifest-ordered merge
 * using a PDF library). Phase 7 uses a fake implementation for pipeline
 * composition tests.</p>
 */
public interface PdfAssemblyStrategy {

    /**
     * Combines the given per-page PDFs into one document.
     *
     * @param pages ordered list of per-page PDF byte arrays; must not be {@code null}
     * @return combined PDF bytes; never {@code null}
     * @throws IOException if assembly fails
     */
    byte[] assemble(List<byte[]> pages) throws IOException;
}
