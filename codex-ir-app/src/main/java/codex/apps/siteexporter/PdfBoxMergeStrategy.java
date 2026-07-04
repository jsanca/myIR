package codex.apps.siteexporter;

import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.multipdf.PDFMergerUtility;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * Merges per-page PDF byte arrays into one combined PDF using Apache PDFBox.
 *
 * <p>Returns {@code new byte[0]} when the input list is empty, consistent with
 * the empty-manifest contract tested by {@link PublicationPipelineTest}.</p>
 */
public final class PdfBoxMergeStrategy implements PdfAssemblyStrategy {

    @Override
    public byte[] assemble(final List<byte[]> pages) throws IOException {
        Objects.requireNonNull(pages, "pages");
        if (pages.isEmpty()) {
            return new byte[0];
        }

        final PDFMergerUtility merger = new PDFMergerUtility();
        for (final byte[] page : pages) {
            merger.addSource(new ByteArrayInputStream(page));
        }

        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        merger.setDestinationStream(out);
        merger.mergeDocuments(MemoryUsageSetting.setupMainMemoryOnly());
        return out.toByteArray();
    }
}
