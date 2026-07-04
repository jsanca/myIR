package codex.apps.siteexporter;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * Assembles per-page PDF byte arrays into one combined PDF in the order they are
 * provided, which must be the manifest-driven order determined by the pipeline's
 * {@link PublicationOrderingStrategy}.
 *
 * <p>Returns {@code new byte[0]} when the input list is empty.</p>
 *
 * <p>PDF merge is delegated to {@link PdfBoxMergeStrategy}.</p>
 */
public final class ManifestOrderPdfAssemblyStrategy implements PdfAssemblyStrategy {

    private final PdfBoxMergeStrategy merger = new PdfBoxMergeStrategy();

    @Override
    public byte[] assemble(final List<byte[]> pages) throws IOException {
        Objects.requireNonNull(pages, "pages");
        return merger.assemble(pages);
    }
}
